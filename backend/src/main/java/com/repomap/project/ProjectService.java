package com.repomap.project;

import java.util.List;

import com.repomap.exception.ResourceNotFoundException;
import com.repomap.filesystem.StorageService;
import com.repomap.project.ProjectDtos.CreateProjectRequest;
import com.repomap.project.ProjectDtos.ProjectFileResponse;
import com.repomap.project.ProjectDtos.ProjectResponse;
import com.repomap.project.ProjectDtos.UpdateProjectRequest;
import com.repomap.security.User;
import com.repomap.security.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner-scoped CRUD for imported repositories. Every read path filters by the
 * authenticated owner, so another user's project is invisible rather than 403.
 */
@Service
public class ProjectService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProjectRepository projectRepository;
    private final ProjectFileRepository projectFileRepository;
    private final StorageService storageService;
    private final UserRepository userRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectFileRepository projectFileRepository,
            StorageService storageService,
            UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.projectFileRepository = projectFileRepository;
        this.storageService = storageService;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProjectResponse create(Long ownerId, CreateProjectRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        Project project = new Project(owner, request.name().trim(), request.description(),
                storageService.newStorageKey());
        project.transitionTo(ProjectStatus.CREATED);
        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> list(Long ownerId, int page, int size, String query) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Project> result = (query == null || query.isBlank())
                ? projectRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId, pageable)
                : projectRepository.searchForOwner(ownerId, query.trim(), pageable);
        return result.map(ProjectResponse::from);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long projectId, Long ownerId) {
        return ProjectResponse.from(requireOwned(projectId, ownerId));
    }

    @Transactional(readOnly = true)
    public Project requireOwned(Long projectId, Long ownerId) {
        return projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Project " + projectId + " not found"));
    }

    @Transactional
    public ProjectResponse update(Long projectId, Long ownerId, UpdateProjectRequest request) {
        Project project = requireOwned(projectId, ownerId);
        if (request.name() != null && !request.name().isBlank()) {
            project.setName(request.name().trim());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional
    public void delete(Long projectId, Long ownerId) {
        Project project = requireOwned(projectId, ownerId);
        projectFileRepository.deleteAllForProject(project.getId());
        projectRepository.delete(project);
        storageService.deleteProject(project.getStorageKey());
    }

    @Transactional(readOnly = true)
    public List<ProjectFileResponse> files(Long projectId, Long ownerId) {
        requireOwned(projectId, ownerId);
        return projectFileRepository.findByProjectIdOrderByPath(projectId).stream()
                .map(ProjectFileResponse::from)
                .toList();
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
