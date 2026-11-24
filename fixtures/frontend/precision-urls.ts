import { API, BOARDS } from "./config";

// Expected: GET /api/v2/users, template, confidence 0.9 (a const imported from another file)
export const importedPrefix = () => fetch(API + "/users");

// Expected: GET /api/v2/boards, template, confidence 0.9 (the whole URL is an imported const)
export const importedUrl = () => fetch(BOARDS);

// Expected: PATCH /api/ok, literal, confidence 1 (other options do not hide anything)
export const otherOptions = () => fetch("/api/ok", { headers: { a: "b" }, method: "PATCH" });

// Expected: GET /api/headers, literal, confidence 1 (no method means GET)
export const headersOnly = () => fetch("/api/headers", { headers: { a: "b" } });

// Expected: UNKNOWN, url null, dynamic, confidence 0 (the method is a variable)
export const methodVariable = (verb: string) => fetch("/api/x", { method: verb });

// Expected: UNKNOWN, url null, dynamic, confidence 0 (shorthand: the method is a variable)
export const methodShorthand = (method: string) => fetch("/api/y", { method });

// Expected: UNKNOWN, url null, dynamic, confidence 0 (the options live in a variable)
const options = { method: "POST" };
export const optionsVariable = () => fetch("/api/z", options);

// Expected: UNKNOWN, url null, dynamic, confidence 0 (a spread may carry the method)
export const optionsSpread = () => fetch("/api/w", { ...options });

// Expected: GET, url null, dynamic, confidence 0 (the URL starts with something unknown)
export const unknownPrefix = (baseUrl: string) => fetch(`${baseUrl}/api/q`);

// Expected: GET /api/windowfetch, literal, confidence 1
export const viaWindow = () => window.fetch("/api/windowfetch");