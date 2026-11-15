# Manual validation against Focalboard

Before writing any tool code, I checked whether the idea produces real findings.

## Method

1. A throwaway script compared the paths in `server/swagger/swagger.yml` (Swagger 2.0, `basePath: /api/v2`) with the `/api/v2/...` URLs found in `webapp/src/octoClient.ts`.
2. Every divergence was then checked against the route registrations in the Go server (`HandleFunc`).

## Findings

| Frontend calls | Spec says | Go server | Verdict |
|---|---|---|---|
| `POST /teams/{teamID}/onboard` | `/team/{teamID}/onboard` (singular) | `/teams/{teamID}/onboard` | Typo in the spec |
| `PUT /teams/{teamID}/categories/reorder` | missing | exists | Undocumented endpoint |
| `PUT /teams/{teamID}/categories/{categoryID}/boards/reorder` | missing | exists | Undocumented endpoint |
| `POST /content-blocks/{blockID}/moveto/{where}/{dstBlockID}` | missing | exists | Undocumented endpoint |

These are documentation drifts, not runtime bugs: the server has every route the frontend calls.

## What the real extractor must handle

The throwaway script only saw complete URL literals. It produced false positives because the client builds most URLs in other ways:

- `fetch(this.getBaseURL() + path)` where `path` is a local variable defined earlier in the function.
- Class helpers that return a path (`teamPath()` returns a template string with one variable).
- Segments joined by concatenation (`this.teamPath() + '/users'`).
- Query strings appended to a template (`.../duplicate${query}`).
- Path parameters named differently on each side (`{boardID}` vs `${boardId}`), so they must be matched by position, not by name.
- A `basePath` in the spec that is missing from most paths, but already present in some of them.

## Limits of this target

The Focalboard spec almost never declares `properties`, so it cannot be used to test field-level drift (`MISSING_FIELD`). A second target is needed for that rule.