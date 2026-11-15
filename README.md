# rift

Static CI tool that detects **contract drift** between a backend's OpenAPI spec and the frontend code that actually consumes it.

Core idea: you don't need frontend types, you need **usage sites**. If the code reads `data.owner`, that is a contract requirement, even when the type is `any`.

> Work in progress.