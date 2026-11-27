import { parentPort } from "node:worker_threads";
import { analyzeResume, type ResumeAnalysisMetadata, type ResumeAnalysisStage } from "./analysis.js";
import { analyzeOpportunity } from "./jd-analysis.js";
import { matchOpportunities, matchOpportunity, type OpportunityMatchInput } from "./opportunity-matching.js";

if (!parentPort) {
  throw new Error("inference-worker must be run inside a worker thread");
}

type WorkerRequest =
  | { id: number; type: "resume"; bytes: Uint8Array; filename: string; contentType: string; modelPath: string }
  | { id: number; type: "opportunity"; title: string; description: string; jdModelPath: string }
  | { id: number; type: "match"; input: OpportunityMatchInput; jdModelPath: string }
  | { id: number; type: "match-batch"; inputs: OpportunityMatchInput[]; jdModelPath: string };

function serializeError(error: unknown) {
  if (error instanceof Error) {
    return {
      name: error.name,
      message: error.message,
      stack: error.stack,
      code: (error as { code?: unknown }).code
    };
  }
  return { name: "Error", message: String(error) };
}

parentPort.on("message", async (msg: WorkerRequest) => {
  try {
    if (msg.type === "resume") {
      const reportStage = (stage: ResumeAnalysisStage, metadata?: ResumeAnalysisMetadata) => {
        parentPort?.postMessage({ id: msg.id, kind: "stage", stage, metadata });
      };
      const result = await analyzeResume(msg.bytes, msg.filename, msg.contentType, msg.modelPath, reportStage);
      parentPort?.postMessage({ id: msg.id, kind: "result", result });
      return;
    }
    if (msg.type === "opportunity") {
      const result = await analyzeOpportunity(msg.title, msg.description, msg.jdModelPath);
      parentPort?.postMessage({ id: msg.id, kind: "result", result });
      return;
    }
    if (msg.type === "match") {
      const result = await matchOpportunity(msg.input, msg.jdModelPath);
      parentPort?.postMessage({ id: msg.id, kind: "result", result });
      return;
    }
    if (msg.type === "match-batch") {
      const result = await matchOpportunities(msg.inputs, msg.jdModelPath);
      parentPort?.postMessage({ id: msg.id, kind: "result", result });
      return;
    }
  } catch (error) {
    parentPort?.postMessage({ id: msg.id, kind: "error", error: serializeError(error) });
  }
});

