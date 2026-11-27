package com.repomap.project;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByIdAndOwnerId(Long id, Long ownerId);

    Page<Project> findByOwnerIdOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    @Query("""
            SELECT p FROM Project p
            WHERE p.owner.id = :ownerId
              AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY p.createdAt DESC
            """)
    Page<Project> searchForOwner(@Param("ownerId") Long ownerId, @Param("query") String query, Pageable pageable);
}
