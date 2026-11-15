// Expected: GET /api/projects, literal, confidence 1
export function listProjects() {
  return fetch("/api/projects");
}

// Expected: POST /api/projects, literal, confidence 1
export function createProject(body: unknown) {
  return fetch("/api/projects", { method: "POST", body: JSON.stringify(body) });
}

// Expected: DELETE /api/projects/legacy, literal, confidence 1 (method is lowercase in the source)
export function deleteLegacy() {
  return fetch("/api/projects/legacy", { method: "delete" });
}

// Expected: GET, url null, dynamic, confidence 0 (the URL is a variable, not resolvable yet)
export function loadFrom(url: string) {
  return fetch(url);
}