package com.repomap.parser;

/** One HTTP route discovered in the source, regardless of framework style. */
public record ParsedEndpoint(String httpMethod, String path, String handler, String className, int lineStart) {
}