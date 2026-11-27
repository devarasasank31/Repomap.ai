package com.repomap.parser;

/**
 * What a parser found in a source file. Values are stored as strings in the
 * database so new languages can be added without a schema change.
 */
public enum SymbolKind {

    CLASS,
    INTERFACE,
    ENUM,
    RECORD,
    ANNOTATION,
    ALIAS,
    METHOD,
    CONSTRUCTOR,
    FIELD,
    FUNCTION,
    CONSTANT;

    public static SymbolKind of(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}