// Expected: GET /api/projects/{id}, template, confidence 0.8 (concatenation with a parameter)
export function getProject(id: string) {
  return fetch("/api/projects/" + id);
}

// Expected: GET /api/projects/active, template, confidence 0.9 (two literals joined)
export function listActive() {
  return fetch("/api/" + "projects/active");
}

// Expected: GET /api/projects, template, confidence 0.9 (URL stored in a local const)
export function listProjects() {
  const path = "/api/projects";
  return fetch(path);
}

// Expected: DELETE /api/projects/{id}, template, confidence 0.8 (local const holding a template)
export function removeProject(id: string) {
  const path = `/api/projects/${id}`;
  return fetch(path, { method: "DELETE" });
}

// Expected: POST /api/projects/{id}/archive, template, confidence 0.8 (const plus concatenation)
export function archiveProject(id: string) {
  const base = "/api/projects/" + id;
  return fetch(base + "/archive", { method: "POST" });
}

// Expected: GET, url null, dynamic, confidence 0 (a function parameter has no fixed text)
export function loadAny(path: string) {
  return fetch(path);
}