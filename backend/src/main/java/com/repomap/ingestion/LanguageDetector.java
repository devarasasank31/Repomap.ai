package com.repomap.ingestion;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Maps file names to a language label and decides which files are worth
 * keeping in the inventory. Anything not recognised is skipped rather than
 * rejected, so a repository may contain binary assets without failing import.
 */
public final class LanguageDetector {

    private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
            Map.entry("java", "java"),
            Map.entry("js", "javascript"),
            Map.entry("jsx", "javascript"),
            Map.entry("mjs", "javascript"),
            Map.entry("cjs", "javascript"),
            Map.entry("ts", "typescript"),
            Map.entry("tsx", "typescript"),
            Map.entry("mts", "typescript"),
            Map.entry("cts", "typescript"),
            Map.entry("xml", "xml"),
            Map.entry("json", "json"),
            Map.entry("yml", "yaml"),
            Map.entry("yaml", "yaml"),
            Map.entry("properties", "properties"),
            Map.entry("sql", "sql"),
            Map.entry("md", "markdown"),
            Map.entry("txt", "text"),
            Map.entry("gradle", "groovy"),
            Map.entry("kts", "kotlin"),
            Map.entry("kt", "kotlin"),
            Map.entry("toml", "toml"),
            Map.entry("sh", "shell"),
            Map.entry("bash", "shell"),
            Map.entry("ini", "config"),
            Map.entry("cfg", "config"),
            Map.entry("conf", "config"),
            Map.entry("csv", "data"));

    /** Extension-less files that are still useful context for the analyser. */
    private static final Map<String, String> BY_FILENAME = Map.of(
            "dockerfile", "dockerfile",
            "makefile", "makefile",
            "license", "text",
            "procfile", "config");

    /** Files that may hold credentials are never extracted. */
    private static final Set<String> SECRET_SUFFIXES = Set.of(
            ".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".der", ".asc", ".ppk", ".pub");
    private static final Set<String> SECRET_NAMES = Set.of(
            "id_rsa", "id_dsa", "id_ed25519", "id_ecdsa", "credentials", "credential", "secrets", "secret");

    private LanguageDetector() {
    }

    public static Optional<String> languageFor(String filename) {
        if (filename == null || filename.isBlank()) {
            return Optional.empty();
        }
        String lower = filename.toLowerCase();
        String extension = extensionOf(filename);
        if (!extension.isEmpty()) {
            return Optional.ofNullable(BY_EXTENSION.get(extension));
        }
        return Optional.ofNullable(BY_FILENAME.get(lower));
    }

    /** @return the lowercase extension without the dot, or "" when there is none */
    public static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    public static boolean isSecretLooking(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        if (lower.startsWith(".env") || lower.equals(".npmrc") || lower.equals(".netrc")) {
            return true;
        }
        if (SECRET_NAMES.contains(lower)) {
            return true;
        }
        for (String suffix : SECRET_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
