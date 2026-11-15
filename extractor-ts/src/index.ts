import { CallExpression, Node, Project, SyntaxKind } from "ts-morph";
import { writeFileSync } from "node:fs";
import { relative, resolve, sep } from "node:path";

type UrlKind = "literal" | "template" | "dynamic";

interface ConsumedCall {
  method: string;
  url: string | null;
  urlKind: UrlKind;
  file: string;
  line: number;
  confidence: number;
  fieldsRead: string[];
}

interface ConsumptionGraph {
  version: 1;
  extractor: "ts";
  calls: ConsumedCall[];
}

// Glob patterns need forward slashes, even on Windows.
const toPosix = (path: string): string => path.split(sep).join("/");

function isFetchCall(call: CallExpression): boolean {
  return call.getExpression().getText() === "fetch";
}

function resolveUrl(arg: Node | undefined): { url: string | null; kind: UrlKind } {
  if (arg && (Node.isStringLiteral(arg) || Node.isNoSubstitutionTemplateLiteral(arg))) {
    return { url: arg.getLiteralText(), kind: "literal" };
  }
  return { url: null, kind: "dynamic" };
}

function methodOf(call: CallExpression): string {
  const options = call.getArguments()[1];
  if (options && Node.isObjectLiteralExpression(options)) {
    const property = options.getProperty("method");
    if (property && Node.isPropertyAssignment(property)) {
      const value = property.getInitializer();
      if (value && Node.isStringLiteral(value)) {
        return value.getLiteralText().toUpperCase();
      }
    }
  }
  return "GET";
}

function extract(directory: string): ConsumptionGraph {
  const root = resolve(directory);
  const posixRoot = toPosix(root);

  const project = new Project({ skipAddingFilesFromTsConfig: true });
  project.addSourceFilesAtPaths([
    `${posixRoot}/**/*.{ts,tsx}`,
    `!${posixRoot}/**/node_modules/**`,
    `!${posixRoot}/**/*.d.ts`,
  ]);

  const calls: ConsumedCall[] = [];
  for (const sourceFile of project.getSourceFiles()) {
    for (const call of sourceFile.getDescendantsOfKind(SyntaxKind.CallExpression)) {
      if (!isFetchCall(call)) continue;

      const { url, kind } = resolveUrl(call.getArguments()[0]);
      calls.push({
        method: methodOf(call),
        url,
        urlKind: kind,
        file: toPosix(relative(root, sourceFile.getFilePath())),
        line: call.getStartLineNumber(),
        confidence: kind === "literal" ? 1.0 : 0.0,
        fieldsRead: [],
      });
    }
  }

  return { version: 1, extractor: "ts", calls };
}

function main(): void {
  const [directory, outputFile] = process.argv.slice(2);
  if (!directory) {
    console.error("usage: rift-extractor-ts <frontend-directory> [output.json]");
    process.exit(2);
  }

  const json = JSON.stringify(extract(directory), null, 2);
  if (outputFile) {
    writeFileSync(outputFile, json);
  } else {
    console.log(json);
  }
}

main();