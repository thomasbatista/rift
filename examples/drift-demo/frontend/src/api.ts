export async function listProjects() {
  const response = await fetch("/api/projects");
  return response.json();
}

export async function createProject(name: string) {
  return fetch("/api/projects", { method: "POST", body: JSON.stringify({ name }) });
}

export async function getProject(id: string) {
  const response = await fetch(`/api/projects/${id}`);
  return response.json();
}

// DRIFT: the backend replaced PUT with PATCH. Nothing in the frontend noticed.
export async function renameProject(id: string, name: string) {
  return fetch(`/api/projects/${id}`, { method: "PUT", body: JSON.stringify({ name }) });
}

// DRIFT: the backend renamed this endpoint to /api/users/me.
export async function loadProfile() {
  const response = await fetch("/api/users/profile");
  return response.json();
}

// rift cannot know this URL, so it says so instead of guessing.
export async function loadAnything(url: string) {
  return fetch(url);
}