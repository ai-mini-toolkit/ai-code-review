package com.aicodereview.common.enums;

import java.util.Map;

/**
 * Programming language detection based on file extension.
 */
public enum Language {
    JAVA, PYTHON, JAVASCRIPT, TYPESCRIPT, GO, RUST, RUBY, PHP, KOTLIN,
    C, CPP, CSHARP, SWIFT,
    YAML, JSON, XML, MARKDOWN, SQL, SHELL, DOCKERFILE,
    UNKNOWN;

    private static final Map<String, Language> EXTENSION_MAP = Map.ofEntries(
            Map.entry(".java", JAVA),
            Map.entry(".py", PYTHON),
            Map.entry(".js", JAVASCRIPT),
            Map.entry(".jsx", JAVASCRIPT),
            Map.entry(".ts", TYPESCRIPT),
            Map.entry(".tsx", TYPESCRIPT),
            Map.entry(".go", GO),
            Map.entry(".rs", RUST),
            Map.entry(".rb", RUBY),
            Map.entry(".php", PHP),
            Map.entry(".kt", KOTLIN),
            Map.entry(".c", C),
            Map.entry(".cpp", CPP),
            Map.entry(".h", C),
            Map.entry(".cs", CSHARP),
            Map.entry(".swift", SWIFT),
            Map.entry(".yml", YAML),
            Map.entry(".yaml", YAML),
            Map.entry(".json", JSON),
            Map.entry(".xml", XML),
            Map.entry(".md", MARKDOWN),
            Map.entry(".sql", SQL),
            Map.entry(".sh", SHELL),
            Map.entry(".dockerfile", DOCKERFILE)
    );

    /**
     * Detects language from a file extension string (e.g., ".java").
     *
     * @param extension the file extension including the dot, or null
     * @return the detected Language, or UNKNOWN if not recognized
     */
    public static Language fromExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return UNKNOWN;
        }
        return EXTENSION_MAP.getOrDefault(extension.toLowerCase(), UNKNOWN);
    }

    /**
     * Detects language from a file name or path (e.g., "src/App.java").
     *
     * @param fileName the file name or path, or null
     * @return the detected Language, or UNKNOWN if not recognized
     */
    public static Language fromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return UNKNOWN;
        }
        String name = fileName.contains("/") ? fileName.substring(fileName.lastIndexOf('/') + 1) : fileName;
        if (name.equals("Dockerfile") || name.endsWith(".dockerfile")) {
            return DOCKERFILE;
        }
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0) {
            return UNKNOWN;
        }
        return fromExtension(name.substring(dotIndex));
    }
}
