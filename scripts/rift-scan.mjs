#!/usr/bin/env node
// One command for the whole pipeline: read the frontend, then compare it with the backend spec.
//
//   node scripts/rift-scan.mjs --frontend <dir> --spec <file|url> [--base-url "<expression>"]... [options]
//
// Options of this script: --frontend, --base-url (repeatable), --graph <file>, --java <path>.
// Everything else (--spec, --min-confidence, --format, --fail-on, --no-color) goes to `rift check`.
import { spawnSync } from "node:child_process";
import { existsSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const MIN_JAVA = 21;
const USAGE_ERROR = 2;

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const extractor = join(root, "extractor-ts", "dist", "index.js");
const jar = join(root, "core", "target", "rift.jar");

// Findings name files relative to the frontend directory. Prefixing them with where that directory
// lives (relative to the current one) lets editors and GitHub annotations find the files.
function filePrefix(directory) {
  const path = relative(process.cwd(), resolve(directory)).split(sep).join("/");
  return path.startsWith("..") || isAbsolute(path) ? "" : path;
}

function fail(message) {
  console.error(`rift-scan: ${message}`);
  process.exit(USAGE_ERROR);
}

const args = process.argv.slice(2);
let frontend;
let graph;
let java = process.env.RIFT_JAVA || "java";
const baseUrls = [];
const forwarded = [];

for (let i = 0; i < args.length; i++) {
  const arg = args[i];
  if (arg === "--frontend") frontend = args[++i];
  else if (arg === "--graph") graph = args[++i];
  else if (arg === "--java") java = args[++i];
  else if (arg === "--base-url") baseUrls.push(args[++i]);
  else forwarded.push(arg);
}

if (!frontend) fail('missing --frontend <directory>. Example: --frontend ./webapp/src --spec ./openapi.json');
if (!existsSync(frontend)) fail(`frontend directory not found: ${frontend}`);
if (!existsSync(extractor)) fail(`extractor is not built. Run "npm install && npm run build" in ${join(root, "extractor-ts")}`);
if (!existsSync(jar)) fail(`rift.jar not found. Build it with Maven ("package") in ${join(root, "core")}`);

// The core is compiled for Java 21: an older java prints a confusing error and exits with 1.
const version = spawnSync(java, ["-version"], { encoding: "utf8" });
if (version.error) fail(`could not run "${java}". Install Java ${MIN_JAVA}+ or pass --java <path> (or set RIFT_JAVA).`);
const major = Number(/version "(\d+)/.exec(version.stderr ?? "")?.[1]);
if (!(major >= MIN_JAVA)) {
  fail(`rift needs Java ${MIN_JAVA} or newer, but "${java}" is ${Number.isNaN(major) ? "unknown" : major}. Pass --java <path> or set RIFT_JAVA.`);
}

const workDir = graph ? null : mkdtempSync(join(tmpdir(), "rift-"));
const graphFile = graph ?? join(workDir, "consumption-graph.json");

let exitCode;
try {
  const extracted = spawnSync(
    process.execPath,
    [extractor, frontend, graphFile, ...baseUrls.flatMap((expression) => ["--base-url", expression])],
    { stdio: "inherit" },
  );
  if (extracted.status !== 0) fail("the TypeScript extractor failed (see the message above)");

  const prefix = filePrefix(frontend);
  const prefixArgs = forwarded.includes("--file-prefix") || !prefix ? [] : ["--file-prefix", prefix];

  const checked = spawnSync(java, ["-jar", jar, "check", "--consumption", graphFile, ...prefixArgs, ...forwarded], { stdio: "inherit" });
  
  exitCode = checked.status ?? USAGE_ERROR;
} finally {
  if (workDir) rmSync(workDir, { recursive: true, force: true });
}

process.exit(exitCode);