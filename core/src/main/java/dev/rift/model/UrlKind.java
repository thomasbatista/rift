package dev.rift.model;

/** How the extractor resolved a URL. Mirrors "urlKind" in consumption-graph.json. */
public enum UrlKind {
    LITERAL,
    TEMPLATE,
    DYNAMIC
}