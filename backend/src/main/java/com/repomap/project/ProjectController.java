package com.repomap.project;

import java.util.List;

import com.repomap.common.PageResponse;
import com.repomap.project.ProjectDtos.CreateProjectRequest;
import com.repomap.project.ProjectDtos.ProjectFileResponse;
import com.repomap.project.ProjectDtos.ProjectResponse;
import com.repomap.project.ProjectDtos.UpdateProjectRequest;
import com.repomap.security.AuthenticatedUser;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(principal.id(), request);
    }

    @GetMapping
    public PageResponse<ProjectResponse> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q) {
        return PageResponse.from(projectService.list(principal.id(), page, size, q));
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable long id) {
        return projectService.get(id, principal.id());
    }

    @PatchMapping("/{id}")
    public ProjectResponse update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable long id,
            @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(id, principal.id(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable long id) {
        projectService.delete(id, principal.id());
    }

    @GetMapping("/{id}/files")
    public List<ProjectFileResponse> files(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable long id) {
        return projectService.files(id, principal.id());
    }
}
