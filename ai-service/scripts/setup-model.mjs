import { createWriteStream, existsSync, mkdirSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { pipeline } from "node:stream/promises";
import { Readable } from "node:stream";

const repo = "oksomu/resume-ner";
const revision = process.env.RESUME_MODEL_REVISION || "main";
const root = resolve(process.env.RESUME_MODEL_PATH || "./models/resume-ner");
const files = [
  "config.json", "tokenizer.json", "tokenizer_config.json", "special_tokens_map.json", "vocab.txt",
  "resume_config.json", "companies.json", "city_country_map.json", "onnx/config.json",
  "onnx/model_quantized.onnx", "onnx/ort_config.json", "onnx/special_tokens_map.json",
  "onnx/tokenizer.json", "onnx/tokenizer_config.json", "onnx/vocab.txt"
];

mkdirSync(root, { recursive: true });
for (const file of files) {
  const destination = resolve(root, file);
  if (existsSync(destination)) continue;
  mkdirSync(dirname(destination), { recursive: true });
  const temporary = `${destination}.download`;
  const response = await fetch(`https://huggingface.co/${repo}/resolve/${revision}/${file}`);
  if (!response.ok || !response.body) throw new Error(`Unable to download required model file ${file} (${response.status})`);
  try {
    await pipeline(Readable.fromWeb(response.body), createWriteStream(temporary));
    renameSync(temporary, destination);
  } catch (error) {
    rmSync(temporary, { force: true });
    throw error;
  }
  process.stdout.write(`Downloaded ${file}\n`);
}

const metadata = await fetch(`https://huggingface.co/api/models/${repo}/revision/${revision}`);
if (metadata.ok) {
  const value = await metadata.json();
  if (typeof value.sha === "string") writeFileSync(resolve(root, ".commit-sha"), value.sha);
}
process.stdout.write(`Local model ready at ${root}\n`);
