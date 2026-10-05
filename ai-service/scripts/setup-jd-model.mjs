import { createWriteStream, existsSync, mkdirSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { pipeline } from "node:stream/promises";
import { Readable } from "node:stream";

const repo = "onnx-community/all-MiniLM-L6-v2-ONNX";
const revision = process.env.JD_MODEL_REVISION || "aff7a1dc4e8a1ea593e6ea21e95c22ef0a25966f";
const root = resolve(process.env.JD_MODEL_PATH || "./models/jd-embedding");
const files = [
  "config.json", "tokenizer.json", "tokenizer_config.json", "special_tokens_map.json", "vocab.txt",
  "onnx/model_q4.onnx", "onnx/model_q4.onnx_data"
];

mkdirSync(root, { recursive: true });
for (const file of files) {
  const destination = resolve(root, file);
  if (existsSync(destination)) continue;
  mkdirSync(dirname(destination), { recursive: true });
  const temporary = `${destination}.download`;
  const response = await fetch(`https://huggingface.co/${repo}/resolve/${revision}/${file}`);
  if (!response.ok || !response.body) throw new Error(`Unable to download required JD model file ${file} (${response.status})`);
  try {
    await pipeline(Readable.fromWeb(response.body), createWriteStream(temporary));
    renameSync(temporary, destination);
  } catch (error) {
    rmSync(temporary, { force: true }); throw error;
  }
  process.stdout.write(`Downloaded ${file}\n`);
}
const metadata = await fetch(`https://huggingface.co/api/models/${repo}/revision/${revision}`);
if (metadata.ok) {
  const value = await metadata.json();
  if (typeof value.sha === "string") writeFileSync(resolve(root, ".commit-sha"), value.sha);
}
process.stdout.write(`Local JD embedding model ready at ${root}\n`);
