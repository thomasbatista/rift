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

interface ResolvedUrl {
  url: string | null;
  kind: UrlKind;
  confidence: number;
}

// Glob patterns need forward slashes, even on Windows.
const toPosix = (path: string): string => path.split(sep).join("/");

function isFetchCall(call: CallExpression): boolean {
  return call.getExpression().getText() === "fetch";
}

function isPlainName(expression: Node): boolean {
  return Node.isIdentifier(expression) || Node.isPropertyAccessExpression(expression);
}

// The name only helps humans read the report: paths are matched by position, not by name.
function placeholderName(expression: Node): string {
  if (Node.isIdentifier(expression)) return expression.getText();
  if (Node.isPropertyAccessExpression(expression)) return expression.getName();
  return "param";
}

function resolveUrl(arg: Node | undefined): ResolvedUrl {
  if (!arg) {
    return { url: null, kind: "dynamic", confidence: 0.0 };
  }

  if (Node.isStringLiteral(arg) || Node.isNoSubstitutionTemplateLiteral(arg)) {
    return { url: arg.getLiteralText(), kind: "literal", confidence: 1.0 };
  }

  if (Node.isTemplateExpression(arg)) {
    let url = arg.getHead().getLiteralText();
    let allPlain = true;
    for (const span of arg.getTemplateSpans()) {
      const expression = span.getExpression();
      if (!isPlainName(expression)) allPlain = false;
      url += `{${placeholderName(expression)}}` + span.getLiteral().getLiteralText();
    }
    return { url, kind: "template", confidence: allPlain ? 0.8 : 0.5 };
  }

  return { url: null, kind: "dynamic", confidence: 0.0 };
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

      const { url, kind, confidence } = resolveUrl(call.getArguments()[0]);
      calls.push({
        method: methodOf(call),
        url,
        urlKind: kind,
        file: toPosix(relative(root, sourceFile.getFilePath())),
        line: call.getStartLineNumber(),
        confidence,
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