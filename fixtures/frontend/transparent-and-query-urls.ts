// Expected: GET /api/users/{userId}, template, confidence 0.8 (encodeURIComponent does not change what the value is)
export function getUser(userId: string) {
  return fetch(`/api/users/${encodeURIComponent(userId)}`);
}

// Expected: POST /api/boards/{boardId}/duplicate, template, confidence 0.8 (the let only ever holds a query string)
export function duplicate(boardId: string, asTemplate: boolean, toTeam?: string) {
  let query = "";
  if (asTemplate) {
    query = "?asTemplate=true";
  }
  if (toTeam) {
    query += `&toTeam=${encodeURIComponent(toTeam)}`;
  }
  const path = `/api/boards/${boardId}/duplicate${query}`;
  return fetch(path, { method: "POST" });
}

// Expected: PUT /api/categories/{categoryId}/boards/{boardId}, template, confidence 0.8 (value || "0" is still one segment)
export function move(categoryId: string | undefined, boardId: string) {
  return fetch(`/api/categories/${categoryId || "0"}/boards/${boardId}`, { method: "PUT" });
}

// Expected: GET, url null, dynamic, confidence 0 (a let that starts as a path is replaced by something else)
export function stillAmbiguous(flag: boolean) {
  let path = "/api/a";
  if (flag) {
    path = "?x=1";
  }
  return fetch(path);
}