package com.repomap.parser;

import java.util.List;

/**
 * One declaration discovered in a source file.
 *
 * @param kind          what it is, see {@link SymbolKind}
 * @param name          simple name as written in the source
 * @param qualifiedName package-qualified name where the language provides one
 * @param signature     short human readable signature (never the body)
 * @param parentName    enclosing type name, {@code null} for top level symbols
 * @param annotations   annotation/decorator names, without arguments
 * @param lineStart     1-based first line
 * @param lineEnd       1-based last line (same as {@code lineStart} when unknown)
 */
public record ParsedSymbol(
        SymbolKind kind,
        String name,
        String qualifiedName,
        String signature,
        String parentName,
        List<String> annotations,
        int lineStart,
        int lineEnd) {

    public ParsedSymbol {
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
    }
}