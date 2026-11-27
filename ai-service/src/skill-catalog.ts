import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

export type SkillCatalogEntry = {
  displayName: string;
  aliases: string[];
  textAliases?: string[];
  caseSensitiveTextAliases?: string[];
};

type RawCatalog = {
  version: number;
  stopwords: string[];
  invalidCandidates: string[];
  skills: SkillCatalogEntry[];
};

const __dirname = dirname(fileURLToPath(import.meta.url));
const catalogPath = resolve(__dirname, "skills-catalog.json");
const rawCatalog = JSON.parse(readFileSync(catalogPath, "utf8")) as RawCatalog;

export const stopwords = new Set(rawCatalog.stopwords.map((item) => item.toLowerCase()));
export const invalidCandidates = new Set(rawCatalog.invalidCandidates.map((item) => item.toLowerCase()));

export const canonicalSkills = new Map<string, string>();
for (const entry of rawCatalog.skills) {
  canonicalSkills.set(entry.displayName.toLowerCase(), entry.displayName);
  for (const alias of entry.aliases) {
    canonicalSkills.set(alias.toLowerCase(), entry.displayName);
  }
}

type CompiledMatcher = {
  displayName: string;
  catalogOrder: number;
  aliasLength: number;
  regex: RegExp;
};

const textMatchers: CompiledMatcher[] = [];
const leadingBoundary = "(^|[^a-zA-Z0-9+#.])";
const trailingBoundary = "(?=$|[^a-zA-Z0-9+#.]|\\.(?=$|\\s|[,;!?)]))";
const caseSensitiveLeading = "(^|[^a-zA-Z0-9+#./])";
const caseSensitiveTrailing = "(?=$|[^a-zA-Z0-9+#./]|\\.(?=$|\\s|[,;!?)]))";

rawCatalog.skills.forEach((entry, catalogOrder) => {
  const insensitive = entry.textAliases ?? entry.aliases;
  for (const alias of insensitive) {
    const escaped = alias.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    textMatchers.push({
      displayName: entry.displayName,
      catalogOrder,
      aliasLength: alias.length,
      regex: new RegExp(`${leadingBoundary}(${escaped})${trailingBoundary}`, "gi")
    });
  }
  for (const alias of entry.caseSensitiveTextAliases ?? []) {
    const escaped = alias.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    textMatchers.push({
      displayName: entry.displayName,
      catalogOrder,
      aliasLength: alias.length,
      regex: new RegExp(`${caseSensitiveLeading}(${escaped})${caseSensitiveTrailing}`, "g")
    });
  }
});
// Match longer phrases first (e.g. "Tailwind CSS" before "CSS", "Spring Boot" before "Spring")
textMatchers.sort((a, b) => b.aliasLength - a.aliasLength);

/**
 * Normalizes and filters candidate skill strings against the canonical skill catalog.
 */
export function normalizeSkills(values: string[]): string[] {
  const seen = new Set<string>();
  const normalized: string[] = [];
  for (const value of values) {
    if (typeof value !== "string") continue;
    const clean = value.trim().replace(/\s+/g, " ");
    if (!clean || clean.length > 100) continue;
    const lower = clean.toLowerCase();

    if (stopwords.has(lower) || invalidCandidates.has(lower)) continue;
    if (!/[a-z0-9]/i.test(clean)) continue;

    const candidate = canonicalSkills.get(lower);
    if (!candidate) continue;

    const key = candidate.toLocaleLowerCase("en-US");
    if (!seen.has(key)) {
      seen.add(key);
      normalized.push(candidate);
    }
  }
  if (seen.has("html/css")) return normalized.filter((skill) => !["html", "css"].includes(skill.toLowerCase()));
  return normalized;
}

/**
 * Scans free-form text (resume body or job description) for canonical skills using boundary-aware,
 * longest-match-first patterns so multi-word skills (e.g. "Tailwind CSS") don't double-match substrings ("CSS").
 */
export function scanSkillsInText(text: string): string[] {
  if (!text) return [];
  const occupied: Array<[number, number]> = [];
  const hits: Array<{ catalogOrder: number; displayName: string }> = [];

  for (const matcher of textMatchers) {
    matcher.regex.lastIndex = 0;
    let match: RegExpExecArray | null;
    while ((match = matcher.regex.exec(text)) !== null) {
      const start = match.index + match[1].length;
      const end = start + match[2].length;
      const overlaps = occupied.some(([s, e]) => start < e && end > s);
      if (!overlaps) {
        occupied.push([start, end]);
        hits.push({ catalogOrder: matcher.catalogOrder, displayName: matcher.displayName });
      }
      if (matcher.regex.lastIndex === match.index) matcher.regex.lastIndex++;
    }
  }

  hits.sort((a, b) => a.catalogOrder - b.catalogOrder);
  return normalizeSkills(hits.map((hit) => hit.displayName));
}
