# rift

[![CI](https://github.com/thomasbatista/rift/actions/workflows/ci.yml/badge.svg)](https://github.com/thomasbatista/rift/actions/workflows/ci.yml)

**Catches contract drift between a backend API spec and the frontend code that really calls it.**

Rename a field or an endpoint on the backend and everything still compiles, every test still passes, and the frontend breaks in production. rift reads your frontend code, compares the HTTP calls it finds with your OpenAPI/Swagger spec, and fails the pull request when they disagree.

It is static analysis only: rift reads source files and a spec. It never calls your server and never touches a database.

## The idea

You do not need types on the frontend. You need the **usage sites**. If the code calls `PUT /api/projects/${id}`, that is a requirement on the backend, even when everything around it is `any`.

That is what makes rift work on code as it already is. Client generation and contract testing (Pact) both require the team to change how it works first. rift does not.

## See it work

[`examples/drift-demo`](examples/drift-demo) is a tiny project with two planted drifts: the backend replaced `PUT` with `PATCH`, and renamed `/api/users/profile` to `/api/users/me`.

```
$ node scripts/rift-scan.mjs --frontend examples/drift-demo/frontend --spec examples/drift-demo/openapi.json

rift  examples/drift-demo/openapi.json
Frontend calls read: 5 of 6 (83%)

MISSING_ENDPOINT (2)
  PUT    /api/projects/{id}   examples/drift-demo/frontend/src/api.ts:17   [0.80]
      The backend declares /api/projects/{id} only for GET, PATCH, not for PUT
  GET    /api/users/profile   examples/drift-demo/frontend/src/api.ts:22   [1.00]
      The backend does not declare GET /api/users/profile

ORPHAN_ENDPOINT (3)
  GET    /api/health   [0.83]
  PATCH  /api/projects/{id}   [0.83]
  GET    /api/users/me   [0.83]

2 missing, 3 orphan.
1 frontend call(s) could not be resolved and were not checked.
```

The exit code is `1`, so a CI job fails. Notice the two orphans `PATCH /api/projects/{id}` and `GET /api/users/me`: they exist on the backend and nobody calls them, which is the fingerprint of a rename the frontend did not follow.

The one call rift could not read (`fetch(url)` with `url` coming from a parameter) is reported as unread instead of guessed.

## Use it in GitHub Actions

```yaml
- uses: actions/checkout@v7
- uses: thomasbatista/rift@main
  with:
    frontend: webapp/src
    spec: server/openapi.json
    base-url: this.getBaseURL()   # optional, see below
```

Findings that fail the build show up as error annotations on the exact file and line. The action builds rift from source on every run (1 to 2 minutes) until there is a released jar.

## Run it locally

You need Java 21+, Node 22+ and Maven.

```bash
mvn -f core/pom.xml package            # builds core/target/rift.jar
npm --prefix extractor-ts ci
npm --prefix extractor-ts run build

node scripts/rift-scan.mjs --frontend <frontend-dir> --spec <openapi.json|swagger.yml|url>
```

If `java` on your PATH is older than 21, point to a newer one with `--java <path>` or the `RIFT_JAVA` environment variable.

| Option | Meaning |
|---|---|
| `--frontend <dir>` | Directory with the TypeScript code (`.ts` and `.tsx`). |
| `--spec <file\|url>` | Backend spec: Swagger 2.0 or OpenAPI 3, JSON or YAML. |
| `--base-url "<expr>"` | An expression that holds the server address, such as `this.getBaseURL()`. It is not part of the API path. Repeatable. |
| `--min-confidence <0..1>` | Hide findings below this confidence (default `0.8`). `0` shows everything. |
| `--fail-on missing\|all\|never` | What makes the exit code `1` (default `missing`). |
| `--format text\|json\|github` | Output format (default `text`). |
| `--file-prefix <dir>` | Added in front of file paths. `rift-scan` sets it for you. |
| `--no-color` | Plain output. |

Exit codes: `0` nothing to report, `1` findings, `2` bad usage or unreadable input.

## Real findings: Focalboard

[Focalboard](https://github.com/mattermost-community/focalboard) is a real project with a hand written `fetch` client and a Swagger spec generated from Go comments. At commit `a84bbb65e32edf972856b329417096ac413518e9`, rift reads 68 of the 71 calls in `webapp/src` and reports 12 missing endpoints and 18 orphans. I checked every finding below against the Go server source:

| Frontend calls | The spec says | The Go server registers | What it is |
|---|---|---|---|
| `PUT /users/{userID}/config` | `PATCH` | `PUT` (`api/users.go:20`) | The spec documents the wrong method |
| `POST /teams/{teamID}/{boardID}/files` | `/teams/{teamID}/boards/{boardID}/files` | `/teams/{teamID}/{boardID}/files` (`api/files.go:80`) | The spec documents the wrong path |
| `POST /teams/{teamID}/onboard` | `/team/{teamID}/onboard` | `/teams/{teamID}/onboard` (`api/onboarding.go:13`) | Typo in the spec (singular `team`) |
| `PATCH /teams/{teamID}/blocks` | nothing | no such route | The client method `patchBlocks` has no callers anywhere in the webapp |
| `POST /teams/{teamID}/boards` | `POST /boards` | only `GET` on that path | The client method `createBoard` has no callers either |
| `PUT .../categories/{id}/boards/{id}/hide` and `unhide`, `POST /content-blocks/{id}/moveto/...`, `GET /files/teams/.../{filename}/info`, `POST /teams/{teamID}/users` | not in the spec | registered in `api/categories.go`, `content_blocks.go`, `files.go`, `teams.go` | Routes the server has and the spec never documents |

None of these breaks the running app: the server has every route the client code that is actually used calls. What they show is a spec that drifted from the code, and two dead client methods. The first three would mislead anyone generating a client from that spec.

To reproduce:

```bash
git clone https://github.com/mattermost-community/focalboard.git
git -C focalboard checkout a84bbb65e32edf972856b329417096ac413518e9
node scripts/rift-scan.mjs \
  --frontend focalboard/webapp/src \
  --spec focalboard/server/swagger/swagger.yml \
  --base-url "this.getBaseURL()"
```

## What rift does not do (yet)

rift is an early version, and it would rather say nothing than guess. The honest list is in [LIMITATIONS.md](LIMITATIONS.md). The short version: it only understands `fetch` calls in `.ts`/`.tsx` files, it checks endpoints and methods but not response fields, and a URL it cannot work out is reported as unread.

## Roadmap

- [ ] `axios` support
- [ ] `MISSING_FIELD`: fields the frontend reads that the backend does not return
- [ ] A released jar, so the GitHub Action starts in seconds
- [ ] A summary comment on the pull request
- [ ] `.js`/`.jsx` files and other extractors (Kotlin, Swift, Dart)

## How it is built

A TypeScript extractor reads the frontend with `ts-morph` and writes a small JSON file. A Java core compares that file with the spec. The two halves only share that file format. [ARCHITECTURE.md](ARCHITECTURE.md) explains the design and why it is split in two languages.

## License

MIT