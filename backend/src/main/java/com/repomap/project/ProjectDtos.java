package com.repomap.project;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.repomap.project.ProjectStatus;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description) {
    }

    public record UpdateProjectRequest(
            @Size(max = 200) String name,
            @Size(max = 1000) String description) {
    }

    public record ProjectResponse(
            long id,
            String name,
            String description,
            ProjectStatus status,
            String sourceFilename,
            int fileCount,
            long totalSizeBytes,
            int javaFileCount,
            int classCount,
            int methodCount,
            int endpointCount,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt) {

        public static ProjectResponse from(Project project) {
            return new ProjectResponse(
                    project.getId(),
                    project.getName(),
                    project.getDescription(),
                    ProjectStatus.of(project.getStatus()),
                    project.getSourceFilename(),
                    project.getFileCount(),
                    project.getTotalSizeBytes(),
                    project.getJavaFileCount(),
                    project.getClassCount(),
                    project.getMethodCount(),
                    project.getEndpointCount(),
                    project.getErrorMessage(),
                    project.getCreatedAt(),
                    project.getUpdatedAt());
        }
    }

    public record ProjectFileResponse(
            long id, String path, String directory, String filename, String extension, String language,
            long sizeBytes, int lineCount, boolean analyzed) {

        public static ProjectFileResponse from(ProjectFile file) {
            return new ProjectFileResponse(
                    file.getId(), file.getPath(), file.getDirectory(), file.getFilename(), file.getExtension(),
                    file.getLanguage(), file.getSizeBytes(), file.getLineCount(), file.isAnalyzed());
        }
    }
}
