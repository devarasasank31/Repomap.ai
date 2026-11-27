package com.repomap.dependency;

import java.util.LinkedHashSet;
import java.util.Set;

/** Represents a project-level dependency with group, artifact, and version. */
public record DependencySymbol(
        String groupId,
        String artifactId,
        String version) {

    /** Human-readable "group:artifact:version" string. */
    public String canonical() {
        return groupId + ":" + artifactId + ":" + version;
    }

    /** True if this dependency version is a dynamic/placeholder version. */
    public boolean isDynamic() {
        return version.contains("-SNAPSHOT") || version.contains("${") || "*".equals(version);
    }
}