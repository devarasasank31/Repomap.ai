package com.repomap.ingestion;

import com.repomap.project.ProjectDtos.ProjectResponse;
import com.repomap.security.AuthenticatedUser;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Repository import endpoints. Uploads are streamed straight into the secure
 * extractor — no temporary file is kept and nothing is ever executed.
 */
@RestController
@RequestMapping("/api/projects")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /** One-shot import: creates a project and extracts the archive into it. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse importArchive(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description) {
        return ProjectResponse.from(
                ingestionService.importArchive(principal.id(), file, name, description).project());
    }

    /** Re-imports a new archive into an existing owned project. */
    @PostMapping(value = "/{id}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProjectResponse importIntoProject(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable long id,
            @RequestParam("file") MultipartFile file) {
        return ProjectResponse.from(ingestionService.importIntoExisting(id, principal.id(), file).project());
    }

    /** Reads one source file with path containment enforced server-side. */
    @GetMapping("/{id}/source")
    public IngestionService.SourceContent source(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable long id,
            @RequestParam String path) {
        return ingestionService.readSource(id, principal.id(), path);
    }
}
