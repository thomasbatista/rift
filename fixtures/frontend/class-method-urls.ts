// Run with: --base-url "this.getBaseURL()"
export class Client {
  private getBaseURL(): string {
    return "https://example.com";
  }

  private teamPath(teamId?: string): string {
    return `/api/v2/teams/${teamId}`;
  }

  private teamsPath(): string {
    return "/api/v2/teams";
  }

  // Expected: GET /api/v2/teams/{teamId}/users, template, confidence 0.8
  async getTeamUsers() {
    const path = this.teamPath() + "/users";
    return fetch(this.getBaseURL() + path);
  }

  // Expected: GET /api/v2/teams, template, confidence 0.9
  async getTeams() {
    return fetch(this.getBaseURL() + this.teamsPath());
  }

  // Expected: POST /api/v2/teams/{teamId}/boards/{boardId}/members, template, confidence 0.8
  async addMember(boardId: string) {
    return fetch(`${this.getBaseURL()}${this.teamPath()}/boards/${boardId}/members`, { method: "POST" });
  }

  private ambiguousPath(flag: boolean): string {
    if (flag) {
      return "/api/v2/a";
    }
    return "/api/v2/b";
  }

  // Expected: GET, url null, dynamic, confidence 0 (the method has two returns)
  async ambiguous(flag: boolean) {
    return fetch(this.getBaseURL() + this.ambiguousPath(flag));
  }
}