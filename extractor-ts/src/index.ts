import {
  CallExpression,
  Node,
  Project,
  SyntaxKind,
  VariableDeclaration,
  VariableDeclarationKind,
} from "ts-morph";
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

const FETCH_NAMES = new Set(["fetch", "window.fetch", "globalThis.fetch", "self.fetch"]);

function isFetchCall(call: CallExpression): boolean {
  return FETCH_NAMES.has(call.getExpression().getText());
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
const TRANSPARENT_CALLS = new Set(["encodeURIComponent", "encodeURI", "String"]);
const DYNAMIC: ResolvedUrl = { url: null, kind: "dynamic", confidence: 0.0 };
const settings = { baseUrlExpressions: new Set<string>() };

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

// "?page=2", "&x=1" or nothing: the part of a URL that is not the path.
function isQueryOrEmpty(parts: Part[]): boolean {
  const first = parts.find((part) => !(part.kind === "text" && part.value === ""));
  if (!first) return true;
  return first.kind === "text" && (first.value.startsWith("?") || first.value.startsWith("&"));
}

// Backend specs describe paths, not query strings, so everything after "?" is dropped.
function stripQuery(parts: Part[]): Part[] {
  const kept: Part[] = [];
  for (const part of parts) {
    if (part.kind === "text" && part.value.includes("?")) {
      kept.push({ kind: "text", value: part.value.split("?")[0] });
      break;
    }
    kept.push(part);
  }
  return kept;
}

// A `let` is only safe to follow when it never changes the path: later `+=` must append
// query strings, and `=` is allowed only for variables that hold nothing but a query string.
function changesThePath(declaration: VariableDeclaration, depth: number): boolean {
  const initializer = declaration.getInitializer();
  const startsAsQuery = initializer ? isQueryOrEmpty(toParts(initializer, depth + 1)) : false;

  for (const reference of declaration.findReferencesAsNodes()) {
    const assignment = reference.getParent();
    if (!assignment || !Node.isBinaryExpression(assignment) || assignment.getLeft() !== reference) continue;

    const operator = assignment.getOperatorToken().getKind();
    if (operator === SyntaxKind.PlusEqualsToken) {
      if (!isQueryOrEmpty(toParts(assignment.getRight(), depth + 1))) return true;
    } else if (operator === SyntaxKind.EqualsToken) {
      if (!startsAsQuery || !isQueryOrEmpty(toParts(assignment.getRight(), depth + 1))) return true;
    } else if (operator >= SyntaxKind.FirstAssignment && operator <= SyntaxKind.LastAssignment) {
      return true;
    }
  }
  return false;
}

function toParts(node: Node, depth: number): Part[] {
  if (depth > MAX_DEPTH) return [paramFor(node)];

  if (settings.baseUrlExpressions.has(node.getText())) {
    return [{ kind: "text", value: "" }];
  }

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

  // cond ? "?a=1" : "" is an optional query string. Mark where the query starts.
  if (Node.isConditionalExpression(node)) {
    const whenTrue = toParts(node.getWhenTrue(), depth + 1);
    const whenFalse = toParts(node.getWhenFalse(), depth + 1);
    if (isQueryOrEmpty(whenTrue) && isQueryOrEmpty(whenFalse)) {
      return [{ kind: "text", value: "?" }];
    }
  }

  // value || "0" and value ?? "0" are still a single path segment.
  if (Node.isBinaryExpression(node)) {
    const operator = node.getOperatorToken().getKind();
    const isFallback = operator === SyntaxKind.BarBarToken || operator === SyntaxKind.QuestionQuestionToken;
    const left = node.getLeft();
    if (isFallback && Node.isStringLiteral(node.getRight()) && (Node.isIdentifier(left) || Node.isPropertyAccessExpression(left))) {
      return [paramFor(left)];
    }
  }

  if (Node.isCallExpression(node) && TRANSPARENT_CALLS.has(node.getExpression().getText())) {
    const [argument] = node.getArguments();
    if (argument) return toParts(argument, depth + 1);
  }

  // this.teamPath() or buildPath(): follow the function when it has a single return.
  if (Node.isCallExpression(node)) {
    const callee = node.getExpression().getSymbol()?.getValueDeclaration();
    if (callee && (Node.isMethodDeclaration(callee) || Node.isFunctionDeclaration(callee))) {
      const returns = callee.getBody()?.getDescendantsOfKind(SyntaxKind.ReturnStatement) ?? [];
      const returned = returns.length === 1 ? returns[0].getExpression() : undefined;
      if (returned) return toParts(returned, depth + 1);
    }
  }

  if (Node.isIdentifier(node)) {
    // A local variable holding a URL: follow it to its initializer.
    let symbol = node.getSymbol();
    if (symbol?.isAlias()) symbol = symbol.getAliasedSymbol() ?? symbol; // imported from another file
    const declaration = symbol?.getValueDeclaration();
    if (declaration && Node.isVariableDeclaration(declaration)) {
      const initializer = declaration.getInitializer();
      const kind = declaration.getVariableStatement()?.getDeclarationKind();
      if (initializer && kind === VariableDeclarationKind.Const) {
        return toParts(initializer, depth + 1);
      }
      if (initializer && kind === VariableDeclarationKind.Let && !changesThePath(declaration, depth)) {
        return toParts(initializer, depth + 1);
      }
    }
  }

  return [paramFor(node)];
}

function resolveUrl(arg: Node | undefined): ResolvedUrl {
  if (!arg) return DYNAMIC;

  if (Node.isStringLiteral(arg) || Node.isNoSubstitutionTemplateLiteral(arg)) {
    return { url: arg.getLiteralText(), kind: "literal", confidence: 1.0 };
  }

  const parts = stripQuery(toParts(arg, 0));

  // A URL that starts with something unknown (a variable we cannot follow) could be any path at all,
  // and one without any fixed text has nothing to match against the backend.
  const first = parts.find((part) => !(part.kind === "text" && part.value === ""));
  if (!first || first.kind === "param") return DYNAMIC;

  const url = parts.map((part) => (part.kind === "text" ? part.value : `{${part.name}}`)).join("");
  const params = parts.filter((part) => part.kind === "param");
  const confidence = params.length === 0 ? 0.9 : params.every((part) => part.plain) ? 0.8 : 0.5;
  return { url, kind: "template", confidence };
}

// null means "there is an options argument we cannot fully read, so the method may be hidden in it".
function methodOf(call: CallExpression): string | null {
  const options = call.getArguments()[1];
  if (!options) return "GET";
  if (!Node.isObjectLiteralExpression(options)) return null;

  let method = "GET";
  for (const property of options.getProperties()) {
    if (Node.isSpreadAssignment(property)) return null;

    if (Node.isShorthandPropertyAssignment(property)) {
      if (property.getName() === "method") return null;
      continue;
    }

    if (Node.isPropertyAssignment(property)) {
      const nameNode = property.getNameNode();
      if (Node.isComputedPropertyName(nameNode)) return null;

      const name = nameNode.getText().replace(/^["']|["']$/g, "");
      if (name !== "method") continue;

      const value = property.getInitializer();
      if (!value || !Node.isStringLiteral(value)) return null;
      method = value.getLiteralText().toUpperCase();
    }
  }
  return method;
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

      const method = methodOf(call);
      const { url, kind, confidence } = method === null ? DYNAMIC : resolveUrl(call.getArguments()[0]);
      calls.push({
        method: method ?? "UNKNOWN",
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

interface Args {
  directory?: string;
  outputFile?: string;
  baseUrls: string[];
}

function parseArgs(argv: string[]): Args {
  const positional: string[] = [];
  const baseUrls: string[] = [];
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--base-url") {
      const value = argv[++i];
      if (!value) {
        console.error("--base-url needs a value");
        process.exit(2);
      }
      baseUrls.push(value);
    } else {
      positional.push(argv[i]);
    }
  }
  return { directory: positional[0], outputFile: positional[1], baseUrls };
}

function main(): void {
  const { directory, outputFile, baseUrls } = parseArgs(process.argv.slice(2));
  if (!directory) {
    console.error('usage: rift-extractor-ts <frontend-directory> [output.json] [--base-url "<expression>"]...');
    process.exit(2);
  }

  baseUrls.forEach((expression) => settings.baseUrlExpressions.add(expression));

  const json = JSON.stringify(extract(directory), null, 2);
  if (outputFile) {
    writeFileSync(outputFile, json);
  } else {
    console.log(json);
  }
}

main();