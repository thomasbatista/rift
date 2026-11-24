# Limitations

rift would rather stay quiet than guess, so most of the list below is things it does not report. Each item was checked against the current code, not written from memory.

## What the extractor reads

- **Only `fetch`** (and `window.fetch`, `globalThis.fetch`, `self.fetch`). `axios`, `XMLHttpRequest` and your own wrappers such as `api.get("/x")` are not detected at all: those calls simply do not show up in the report. `fetch(new URL(...))` and `fetch(new Request(...))` are detected but reported as unread.
- **Only `.ts` and `.tsx` files.** `.js`, `.jsx`, `.vue` and `.svelte` are not scanned.

## What it can work out about a URL

rift rebuilds a URL from string literals, template literals, `+` concatenation, local `const` variables (also when imported from another file), `this.method()` and function calls that have a single `return`, and `let` variables that are only ever extended with a query string. Query strings are dropped, and `encodeURIComponent(x)`, `String(x)` and `value || "0"` count as one path segment.

A call is reported as **unread** (not checked, and counted in the coverage line) when:

- the URL, or its first part, comes from a function parameter, so the path is built by whoever calls the function (3 of the 71 calls in Focalboard);
- it starts with something rift cannot follow, such as a property of a config object;
- it is built with a `let` that is reassigned or receives path text;
- it goes through a function that has more than one `return`, or an arrow function stored in a variable.

## The HTTP method

The method is read only from a plain `method: "POST"` (no `method` means GET). If the options object has a spread, a `{ method }` shorthand, a variable, or is built elsewhere, the method may be hidden, so the call is reported as unread with method `UNKNOWN`. rift does not assume GET.

## How paths are compared

- Parameter names never matter: `{boardId}` and `{boardID}` are the same.
- **A backend `{param}` accepts any value.** If the spec has `/categories/{categoryID}` and the frontend calls `/categories/reorder`, rift sees a match. In Focalboard this hides one undocumented endpoint (`PUT /teams/{id}/categories/reorder`). rift cannot know `reorder` is a route of its own.
- A frontend parameter against fixed text on the backend (`/boards/{id}` vs `/boards/search`) is a **loose** match: accepted only when nothing matches better, so it never hides an unused endpoint that has a proper match.
- A path segment that only contains a parameter (such as `{name}.json`) is treated as a whole parameter.
- Constraints in the spec, such as a regular expression on a path parameter, are ignored. Only the first `servers` entry's path (or the Swagger 2.0 `basePath`) is used as the base path.
- Paths are case sensitive.

## What is compared

- **Endpoints and methods only.** Request and response schemas are not compared: there is no check for fields the frontend reads that the backend does not return, and no type mismatch check. The `fieldsRead` entry of the graph format exists but is always empty. (Some specs, like Focalboard's, do not declare their fields at all.)
- **Orphans only speak about the scanned frontend.** A mobile app, a script or a test may call an endpoint the web frontend does not. Their confidence is the share of frontend calls rift could read.
- rift trusts the spec. A spec that is out of date is exactly the kind of thing it reports, but it cannot tell which side is wrong: that takes a person reading both.

## Everything else

- Needs Java 21+ and Node 22+. Specs must be Swagger 2.0 or OpenAPI 3.
- Speed was measured only on the Focalboard webapp (about 5 seconds on Linux). Larger codebases are untested.
- The GitHub Action compiles rift on every run (1 to 2 minutes).
- Annotations appear inline in the pull request diff only when the reported line is part of the diff. Otherwise they show in the Checks tab.