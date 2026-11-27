package com.repomap.search;

import com.repomap.parser.SymbolKind;

/** Result of a search query over extracted source files and symbols. */
public record SearchResult(
        String query,
        int totalMatches,
        java.util.List<SearchMatch> matches) {

    /** A single match with file path, language, snippet, and symbol context. */
    public record SearchMatch(
            String path,
            String language,
            String snippet,
            int lineNumber,
            SymbolKind kind,
            String symbolName) {
    }
}