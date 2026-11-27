package com.repomap.filesystem;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Hard limits applied while extracting an uploaded archive. They exist to stop
 * decompression bombs, oversized repositories and resource exhaustion.
 */
@Component
public class IngestionLimits {

    private final long maxUploadBytes;
    private final int maxExtractedFiles;
    private final long maxExtractedBytes;
    private final long maxSingleFileBytes;
    private final int maxEntriesScanned;
    private final double maxCompressionRatio;

    public IngestionLimits(
            @Value("${repomap.limits.max-upload-bytes:104857600}") long maxUploadBytes,
            @Value("${repomap.limits.max-extracted-files:20000}") int maxExtractedFiles,
            @Value("${repomap.limits.max-extracted-bytes:524288000}") long maxExtractedBytes,
            @Value("${repomap.limits.max-single-file-bytes:5242880}") long maxSingleFileBytes,
            @Value("${repomap.limits.max-entries-scanned:200000}") int maxEntriesScanned,
            @Value("${repomap.limits.max-compression-ratio:200}") double maxCompressionRatio) {
        this.maxUploadBytes = maxUploadBytes;
        this.maxExtractedFiles = maxExtractedFiles;
        this.maxExtractedBytes = maxExtractedBytes;
        this.maxSingleFileBytes = maxSingleFileBytes;
        this.maxEntriesScanned = maxEntriesScanned;
        this.maxCompressionRatio = maxCompressionRatio;
    }

    public long maxUploadBytes() {
        return maxUploadBytes;
    }

    public int maxExtractedFiles() {
        return maxExtractedFiles;
    }

    public long maxExtractedBytes() {
        return maxExtractedBytes;
    }

    public long maxSingleFileBytes() {
        return maxSingleFileBytes;
    }

    public int maxEntriesScanned() {
        return maxEntriesScanned;
    }

    public double maxCompressionRatio() {
        return maxCompressionRatio;
    }
}
