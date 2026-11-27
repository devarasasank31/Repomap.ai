package com.repomap.project;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectFileRepository extends JpaRepository<ProjectFile, Long> {

    Optional<ProjectFile> findByProjectIdAndPath(Long projectId, String path);

    Page<ProjectFile> findByProjectIdOrderByPath(Long projectId, Pageable pageable);

    List<ProjectFile> findByProjectIdOrderByPath(Long projectId);

    long countByProjectId(Long projectId);

    long countByProjectIdAndLanguage(Long projectId, String language);

    @Query("SELECT DISTINCT f.language FROM ProjectFile f WHERE f.project.id = :projectId AND f.language IS NOT NULL")
    List<String> findDistinctLanguages(@Param("projectId") Long projectId);

    @Modifying
    @Query("DELETE FROM ProjectFile f WHERE f.project.id = :projectId")
    void deleteAllForProject(@Param("projectId") Long projectId);

    @Query("SELECT COALESCE(SUM(f.sizeBytes), 0) FROM ProjectFile f WHERE f.project.id = :projectId")
    long totalSizeForProject(@Param("projectId") Long projectId);
}
