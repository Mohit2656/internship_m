import { Worker } from "node:worker_threads";
import type { ResumeAnalysisMetadata, ResumeAnalysisResult, ResumeAnalysisStage } from "./analysis.js";
import type { OpportunityAnalysisResult } from "./jd-analysis.js";
import type { OpportunityMatchInput, OpportunityMatchResult } from "./opportunity-matching.js";

export class WorkerPoolError extends Error {
  readonly code: string;
  constructor(code: string, message: string) {
    super(message);
    this.name = "WorkerPoolError";
    this.code = code;
  }
}

type PendingTask = {
  id: number;
  payload: Record<string, unknown>;
  onStage?: (stage: ResumeAnalysisStage, metadata?: ResumeAnalysisMetadata) => void;
  resolve: (value: unknown) => void;
  reject: (reason?: unknown) => void;
};

type WorkerSlot = {
  worker: Worker;
  busyTask: PendingTask | null;
  timer: NodeJS.Timeout | null;
};

type WorkerMessage =
  | { id: number; kind: "stage"; stage: ResumeAnalysisStage; metadata?: ResumeAnalysisMetadata }
  | { id: number; kind: "result"; result: unknown }
  | { id: number; kind: "error"; error: { name?: string; message?: string; stack?: string } };

export class InferenceWorkerPool {
  private readonly poolSize: number;
  private readonly maxQueue: number;
  private readonly timeoutMs: number;
  private readonly slots: WorkerSlot[] = [];
  private readonly queue: PendingTask[] = [];
  private nextId = 1;
  private closed = false;

  constructor(options: { poolSize?: number; maxQueue?: number; timeoutMs?: number } = {}) {
    this.poolSize = Math.max(1, options.poolSize ?? Number(process.env.AI_WORKER_POOL_SIZE || 2));
    this.maxQueue = Math.max(1, options.maxQueue ?? Number(process.env.AI_WORKER_MAX_QUEUE || 16));
    this.timeoutMs = Math.max(1000, options.timeoutMs ?? Number(process.env.AI_INFERENCE_TIMEOUT_MS || 110_000));
  }

  private createWorker(): WorkerSlot {
    const isTs = import.meta.url.endsWith(".ts");
    const workerUrl = new URL(isTs ? "./inference-worker.ts" : "./inference-worker.js", import.meta.url);
    const worker = new Worker(workerUrl, isTs ? { execArgv: ["--import", "tsx"] } : undefined);
    const slot: WorkerSlot = { worker, busyTask: null, timer: null };

    worker.on("message", (msg: WorkerMessage) => {
      const task = slot.busyTask;
      if (!task || task.id !== msg.id) return;
      if (msg.kind === "stage") {
        task.onStage?.(msg.stage, msg.metadata);
        return;
      }
      this.clearTask(slot);
      if (msg.kind === "result") {
        task.resolve(msg.result);
      } else {
        const err = new Error(msg.error?.message || "Worker inference failed");
        err.name = msg.error?.name || "Error";
        if (msg.error?.stack) err.stack = msg.error.stack;
        task.reject(err);
      }
      this.pump();
    });

    worker.on("error", (err) => {
      const task = slot.busyTask;
      this.removeSlot(slot);
      if (task) task.reject(err);
      this.pump();
    });

    worker.on("exit", (code) => {
      const task = slot.busyTask;
      this.removeSlot(slot);
      if (task && !this.closed) {
        task.reject(new Error(`Inference worker exited unexpectedly with code ${code}`));
      }
      this.pump();
    });

    this.slots.push(slot);
    return slot;
  }

  private removeSlot(slot: WorkerSlot) {
    if (slot.timer) clearTimeout(slot.timer);
    slot.timer = null;
    slot.busyTask = null;
    const index = this.slots.indexOf(slot);
    if (index >= 0) this.slots.splice(index, 1);
  }

  private clearTask(slot: WorkerSlot) {
    if (slot.timer) clearTimeout(slot.timer);
    slot.timer = null;
    slot.busyTask = null;
  }

  private pump() {
    if (this.closed || this.queue.length === 0) return;
    let idle = this.slots.find((slot) => slot.busyTask === null);
    if (!idle && this.slots.length < this.poolSize) {
      idle = this.createWorker();
    }
    if (!idle) return;
    const task = this.queue.shift()!;
    idle.busyTask = task;
    idle.timer = setTimeout(() => {
      const timedOut = idle!.busyTask;
      this.removeSlot(idle!);
      void idle!.worker.terminate();
      if (timedOut) timedOut.reject(new WorkerPoolError("INFERENCE_TIMEOUT", "Local model inference timed out"));
      this.pump();
    }, this.timeoutMs);
    idle.worker.postMessage({ id: task.id, ...task.payload });
  }

  private run<T>(payload: Record<string, unknown>, onStage?: PendingTask["onStage"]): Promise<T> {
    if (this.closed) return Promise.reject(new WorkerPoolError("SERVER_SHUTTING_DOWN", "Worker pool is closed"));
    const allBusy = this.slots.length >= this.poolSize && this.slots.every((s) => s.busyTask !== null);
    if (allBusy && this.queue.length >= this.maxQueue) {
      return Promise.reject(new WorkerPoolError("SERVER_BUSY", "Inference queue is full; please retry shortly"));
    }
    return new Promise<T>((resolve, reject) => {
      this.queue.push({ id: this.nextId++, payload, onStage, resolve: resolve as (v: unknown) => void, reject });
      this.pump();
    });
  }

  analyzeResume(
    bytes: Uint8Array,
    filename: string,
    contentType: string,
    modelPath: string,
    onStage?: (stage: ResumeAnalysisStage, metadata?: ResumeAnalysisMetadata) => void
  ): Promise<ResumeAnalysisResult> {
    return this.run<ResumeAnalysisResult>({ type: "resume", bytes, filename, contentType, modelPath }, onStage);
  }

  analyzeOpportunity(title: string, description: string, jdModelPath: string): Promise<OpportunityAnalysisResult> {
    return this.run<OpportunityAnalysisResult>({ type: "opportunity", title, description, jdModelPath });
  }

  matchOpportunity(input: OpportunityMatchInput, jdModelPath: string): Promise<OpportunityMatchResult> {
    return this.run<OpportunityMatchResult>({ type: "match", input, jdModelPath });
  }

  matchOpportunities(inputs: OpportunityMatchInput[], jdModelPath: string): Promise<OpportunityMatchResult[]> {
    return this.run<OpportunityMatchResult[]>({ type: "match-batch", inputs, jdModelPath });
  }

  async close(): Promise<void> {
    this.closed = true;
    while (this.queue.length > 0) {
      this.queue.shift()?.reject(new WorkerPoolError("SERVER_SHUTTING_DOWN", "Worker pool is closed"));
    }
    const closing = [...this.slots];
    this.slots.length = 0;
    await Promise.all(
      closing.map(async (slot) => {
        if (slot.timer) clearTimeout(slot.timer);
        await slot.worker.terminate();
      })
    );
  }
}

