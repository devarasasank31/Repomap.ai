package com.repomap.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Minimal parser stub for Phase 3: source code symbol and endpoint extraction. */
public interface JavaParser {

    /** Parse a Java source file and return discovered symbols. */
    List<ParsedSymbol> parseSymbols(Path source) throws IOException;

    /** Parse a Java source file and return discovered HTTP endpoints. */
    List<ParsedEndpoint> parseEndpoints(Path source) throws IOException;
}