# consumption-graph.json

The interface between an **extractor** (reads frontend code) and **rift-core** (compares it against the backend contract).

Any language can be supported by writing an extractor that emits this format.

## Example

```json
{
  "version": 1,
  "extractor": "ts",
  "calls": [
    {
      "method": "GET",
      "url": "/api/projects/{id}",
      "urlKind": "template",
      "file": "src/api/projects.ts",
      "line": 12,
      "confidence": 0.8,
      "fieldsRead": ["name", "owner"]
    }
  ]
}
```

## Fields

| Field | Type | Description |
|---|---|---|
| `version` | number | Format version. Currently `1`. |
| `extractor` | string | Which extractor produced the file (e.g. `ts`). |
| `calls` | array | One entry per HTTP call found in the frontend code. |

### Call

| Field | Type | Description |
|---|---|---|
| `method` | string | HTTP method, uppercase (`GET`, `POST`...). |
| `url` | string or null | Path template, with `{name}` for dynamic segments. `null` when it could not be resolved. |
| `urlKind` | string | `literal`, `template` or `dynamic`. |
| `file` | string | Path of the source file, relative to the scanned directory. |
| `line` | number | Line of the call, starting at 1. |
| `confidence` | number | From `0.0` to `1.0`. How sure the extractor is about `method` and `url`. |
| `fieldsRead` | string[] | Response fields the code reads. Empty until field tracking is implemented. |

## Rules

- `literal`: the URL was a plain string. `confidence` should be `1.0`.
- `template`: the URL was a template string or concatenation that was resolved. `confidence` is below `1.0`.
- `dynamic`: the URL could not be resolved. `url` is `null` and `confidence` is `0.0`. The core must **not** report findings about these calls.
- Precision over coverage: when in doubt, lower the confidence instead of guessing.


## Confidence rules (TypeScript extractor)

| Situation | `urlKind` | `confidence` |
|---|---|---|
| The URL is a plain string | `literal` | 1.0 |
| Resolved through concatenation or a local `const`, with no unknown parts | `template` | 0.9 |
| Resolved, and every unknown part is a plain name (`id`, `member.userId`) | `template` | 0.8 |
| Resolved, but some unknown part is an expression (e.g. a function call) | `template` | 0.5 |
| No fixed text at all (e.g. the URL is a function parameter) | `dynamic` | 0.0 |

Unknown parts are written as `{name}` in `url`. Backends and frontends often name the same parameter differently (`{boardID}` vs `${boardId}`), so the core matches path parameters by position, never by name.


## What the TypeScript extractor resolves

- String literals, template literals and `+` concatenation.
- Local `const` variables holding a URL.
- Local `let` variables, but only when every later change is a query string append (`path += "?x=1"`). A `let` that is reassigned, or that receives path text, has several possible values, so the call is reported as `dynamic`.
- Query strings are removed: backend specs describe paths only.
- An optional query string such as `(cond ? "?a=1" : "")` does not change the path.
- Calls to class methods and plain functions that have a single `return` (e.g. `this.teamPath()`). With two or more returns the call is reported as `dynamic`.
- The server address is not part of the API path. Tell the extractor which expressions hold it with `--base-url "this.getBaseURL()"` (the option can be repeated).
- `encodeURIComponent(x)`, `encodeURI(x)` and `String(x)` only convert a value, so they resolve to the value itself.
- `value || "0"` and `value ?? "0"` count as a single path segment.
- A `let` that only ever holds a query string may be reassigned freely. A `let` that starts as a path may not.