import multipart from "@fastify/multipart";
import Fastify from "fastify";
import { existsSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { resolve } from "node:path";
import { analyzeResume } from "./analysis.js";
import { analyzeOpportunity } from "./jd-analysis.js";
import { matchOpportunity, matchOpportunities, type OpportunityMatchInput } from "./opportunity-matching.js";

const maxBytes = 5 * 1024 * 1024;

export function buildApp(options: { modelPath?: string; analyzer?: typeof analyzeResume; jdModelPath?: string; opportunityAnalyzer?: typeof analyzeOpportunity; opportunityMatcher?: typeof matchOpportunity; opportunityBatchMatcher?: typeof matchOpportunities } = {}) {
  const app = Fastify({ logger: false, bodyLimit: maxBytes + 64 * 1024 });
  const modelPath = resolve(options.modelPath || process.env.RESUME_MODEL_PATH || "./models/resume-ner");
  const analyzer = options.analyzer || analyzeResume;
  const jdModelPath = resolve(options.jdModelPath || process.env.JD_MODEL_PATH || "./models/jd-embedding");
  const opportunityAnalyzer = options.opportunityAnalyzer || analyzeOpportunity;
  const opportunityMatcher = options.opportunityMatcher || matchOpportunity;
  const opportunityBatchMatcher = options.opportunityBatchMatcher || matchOpportunities;
  app.register(multipart, { limits: { files: 1, fileSize: maxBytes, fields: 0 } });

  app.get("/health", async () => ({ status: "UP", modelReady: existsSync(resolve(modelPath, "onnx/model_quantized.onnx")) }));
  app.post("/internal/resume/analyze", async (request, reply) => {
    try {
      const part = await request.file();
      if (!part) return reply.code(400).send({ code: "FILE_REQUIRED", message: "A resume file is required" });
      const bytes = new Uint8Array(await part.toBuffer());
      if (bytes.length === 0) return reply.code(400).send({ code: "EMPTY_DOCUMENT", message: "The resume is empty" });
      return await analyzer(bytes, part.filename, part.mimetype, modelPath);
    } catch (error) {
      const message = error instanceof Error ? error.message : "";
      const clientError = message === "UNSUPPORTED_DOCUMENT" || /empty|unreadable|extract text/i.test(message);
      return reply.code(clientError ? 422 : 500).send({
        code: clientError ? "DOCUMENT_ANALYSIS_FAILED" : "INFERENCE_FAILED",
        message: clientError ? "The resume could not be analyzed" : "Local model inference failed"
      });
    }
  });
  app.post("/internal/opportunities/analyze", async (request, reply) => {
    try {
      const body = request.body as { title?: unknown; description?: unknown } | null;
      if (!body || typeof body.title !== "string" || typeof body.description !== "string")
        return reply.code(400).send({ code: "INVALID_REQUEST", message: "Title and description are required" });
      const title = body.title.trim(); const description = body.description.trim();
      if (!title || !description) return reply.code(422).send({ code: "EMPTY_JOB_DESCRIPTION", message: "Job description is required" });
      if (title.length > 200 || description.length > 30_000)
        return reply.code(422).send({ code: "JOB_DESCRIPTION_TOO_LARGE", message: "Job description is too large" });
      return await opportunityAnalyzer(title, description, jdModelPath);
    } catch {
      return reply.code(500).send({ code: "INFERENCE_FAILED", message: "Local model inference failed" });
    }
  });
  app.post("/internal/opportunities/match", async (request, reply) => {
    try {
      const body = request.body as OpportunityMatchInput | null;
      if (!validMatchInput(body)) return reply.code(400).send({ code: "INVALID_REQUEST", message: "A valid student and opportunity matching payload is required" });
      return await opportunityMatcher(body, jdModelPath);
    } catch {
      return reply.code(500).send({ code: "INFERENCE_FAILED", message: "Local model inference failed" });
    }
  });
  app.post("/internal/opportunities/match-batch", async (request, reply) => {
    try {
      const body = request.body as { matches?: OpportunityMatchInput[] } | null;
      if (!body || !Array.isArray(body.matches) || body.matches.length > 250 || !body.matches.every(validMatchInput))
        return reply.code(400).send({ code: "INVALID_REQUEST", message: "A valid matching batch is required" });
      return { matches: await opportunityBatchMatcher(body.matches, jdModelPath) };
    } catch {
      return reply.code(500).send({ code: "INFERENCE_FAILED", message: "Local model inference failed" });
    }
  });
  return app;
}

function validMatchInput(body: OpportunityMatchInput | null): body is OpportunityMatchInput {
  if (!body || !body.student || !body.opportunity || typeof body.student.headline !== "string" || !Array.isArray(body.student.skills) ||
      !Array.isArray(body.student.careerInterests) || !Array.isArray(body.student.experience) || typeof body.opportunity.title !== "string" ||
      typeof body.opportunity.description !== "string" || !Array.isArray(body.opportunity.requiredSkills) || !Array.isArray(body.opportunity.preferredSkills) ||
      !Array.isArray(body.opportunity.roleSignals) || !Array.isArray(body.opportunity.responsibilitySignals)) return false;
  const strings = [body.student.headline, ...body.student.careerInterests, ...body.student.experience, body.opportunity.title, body.opportunity.description,
    ...body.opportunity.roleSignals, ...body.opportunity.responsibilitySignals];
  const skillValues = [...body.student.skills, ...body.opportunity.requiredSkills, ...body.opportunity.preferredSkills];
  return strings.every(value => typeof value === "string" && value.length <= 30_000) && skillValues.every(value => value && typeof value.name === "string" &&
    typeof value.normalizedName === "string" && value.name.trim().length > 0 && value.normalizedName.trim().length > 0 && value.name.length <= 120 && value.normalizedName.length <= 120) &&
    body.opportunity.title.trim().length > 0 &&
    skillValues.length <= 300 && strings.length <= 300;
}

const isMain = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMain) {
  const app = buildApp();
  const host = process.env.AI_SERVICE_HOST || "127.0.0.1";
  const port = Number(process.env.AI_SERVICE_PORT || 8091);
  await app.listen({ host, port });
  process.stdout.write(`IMS local AI service listening on http://${host}:${port}\n`);
}
