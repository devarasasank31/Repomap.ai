package com.repomap.project;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** One extracted source file belonging to a project. */
@Entity
@Table(name = "project_files")
public class ProjectFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Repository-relative POSIX path, e.g. {@code src/main/java/App.java}. */
    @Column(nullable = false, length = 1024)
    private String path;

    @Column(nullable = false, length = 768)
    private String directory;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(length = 32)
    private String extension;

    @Column(length = 32)
    private String language;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "line_count", nullable = false)
    private int lineCount;

    @Column(nullable = false)
    private boolean analyzed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProjectFile() {
        // JPA
    }

    public ProjectFile(
            Project project, String path, String directory, String filename, String extension, String language,
            long sizeBytes, int lineCount) {
        this.project = project;
        this.path = path;
        this.directory = directory;
        this.filename = filename;
        this.extension = extension;
        this.language = language;
        this.sizeBytes = sizeBytes;
        this.lineCount = lineCount;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getPath() {
        return path;
    }

    public String getDirectory() {
        return directory;
    }

    public String getFilename() {
        return filename;
    }

    public String getExtension() {
        return extension;
    }

    public String getLanguage() {
        return language;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getLineCount() {
        return lineCount;
    }

    public boolean isAnalyzed() {
        return analyzed;
    }

    public void setAnalyzed(boolean analyzed) {
        this.analyzed = analyzed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
