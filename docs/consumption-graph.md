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