import { CallExpression, Node, Project, SyntaxKind, VariableDeclarationKind } from "ts-morph";
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

interface ResolvedUrl {
  url: string | null;
  kind: UrlKind;
  confidence: number;
}

// A URL is rebuilt from pieces: fixed text and parameters we could not resolve.
type Part =
  | { kind: "text"; value: string }
  | { kind: "param"; name: string; plain: boolean };

const MAX_DEPTH = 8;
const DYNAMIC: ResolvedUrl = { url: null, kind: "dynamic", confidence: 0.0 };

// The name only helps humans read the report: paths are matched by position, not by name.
function paramFor(expression: Node): Part {
  if (Node.isIdentifier(expression)) {
    return { kind: "param", name: expression.getText(), plain: true };
  }
  if (Node.isPropertyAccessExpression(expression)) {
    return { kind: "param", name: expression.getName(), plain: true };
  }
  return { kind: "param", name: "param", plain: false };
}

function isConst(declaration: Node): boolean {
  if (!Node.isVariableDeclaration(declaration)) return false;
  return declaration.getVariableStatement()?.getDeclarationKind() === VariableDeclarationKind.Const;
}

function toParts(node: Node, depth: number): Part[] {
  if (depth > MAX_DEPTH) return [paramFor(node)];

  if (Node.isStringLiteral(node) || Node.isNoSubstitutionTemplateLiteral(node)) {
    return [{ kind: "text", value: node.getLiteralText() }];
  }

  if (Node.isParenthesizedExpression(node)) {
    return toParts(node.getExpression(), depth + 1);
  }

  if (Node.isTemplateExpression(node)) {
    const parts: Part[] = [{ kind: "text", value: node.getHead().getLiteralText() }];
    for (const span of node.getTemplateSpans()) {
      parts.push(...toParts(span.getExpression(), depth + 1));
      parts.push({ kind: "text", value: span.getLiteral().getLiteralText() });
    }
    return parts;
  }

  if (Node.isBinaryExpression(node) && node.getOperatorToken().getKind() === SyntaxKind.PlusToken) {
    return [...toParts(node.getLeft(), depth + 1), ...toParts(node.getRight(), depth + 1)];
  }

  if (Node.isIdentifier(node)) {
    // A local const holding a URL: follow it to its initializer.
    const declaration = node.getSymbol()?.getValueDeclaration();
    if (declaration && isConst(declaration) && Node.isVariableDeclaration(declaration)) {
      const initializer = declaration.getInitializer();
      if (initializer) return toParts(initializer, depth + 1);
    }
  }

  return [paramFor(node)];
}

function resolveUrl(arg: Node | undefined): ResolvedUrl {
  if (!arg) return DYNAMIC;

  if (Node.isStringLiteral(arg) || Node.isNoSubstitutionTemplateLiteral(arg)) {
    return { url: arg.getLiteralText(), kind: "literal", confidence: 1.0 };
  }

  const parts = toParts(arg, 0);

  // Without any fixed text there is nothing to match against the backend.
  const hasText = parts.some((part) => part.kind === "text" && part.value.length > 0);
  if (!hasText) return DYNAMIC;

  const url = parts.map((part) => (part.kind === "text" ? part.value : `{${part.name}}`)).join("");
  const params = parts.filter((part) => part.kind === "param");
  const confidence = params.length === 0 ? 0.9 : params.every((part) => part.plain) ? 0.8 : 0.5;
  return { url, kind: "template", confidence };
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