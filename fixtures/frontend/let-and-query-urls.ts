// Expected: GET /api/users/me, template, confidence 0.9 (let, later appends are only query strings)
export function getMe(withRoles: boolean) {
  let path = "/api/users/me";
  if (withRoles) {
    path += "?include=roles";
  }
  return fetch(path);
}

// Expected: GET /api/projects, template, confidence 0.9 (query string removed)
export function listProjects(page: number) {
  return fetch(`/api/projects?page=${page}`);
}

// Expected: GET /api/boards/{boardId}/blocks, template, confidence 0.8 (optional query string removed)
export function listBlocks(boardId: string, sourceId?: string) {
  return fetch(`/api/boards/${boardId}/blocks` + (sourceId ? `?source=${sourceId}` : ""));
}

// Expected: GET, url null, dynamic, confidence 0 (the let is reassigned: two possible URLs)
export function pick(archived: boolean) {
  let path = "/api/projects";
  if (archived) {
    path = "/api/projects/archived";
  }
  return fetch(path);
}

// Expected: GET, url null, dynamic, confidence 0 (the let gets path text appended)
export function maybeById(id?: string) {
  let path = "/api/projects";
  if (id) {
    path += "/" + id;
  }
  return fetch(path);
}