// Expected: GET /api/projects/{id}, template, confidence 0.8
export function getProject(id: string) {
  return fetch(`/api/projects/${id}`);
}

// Expected: PUT /api/projects/{projectId}/members/{userId}, template, confidence 0.8
export function updateMember(projectId: string, member: { userId: string }) {
  return fetch(`/api/projects/${projectId}/members/${member.userId}`, { method: "PUT" });
}

// Expected: GET /api/search/{param}, template, confidence 0.5 (the substitution is a call to an unknown function)
export function search(term: string) {
  return fetch(`/api/search/${slugify(term)}`);
}

// Expected: GET /api/health, literal, confidence 1 (template without substitutions)
export function health() {
  return fetch(`/api/health`);
}

declare function slugify(value: string): string;