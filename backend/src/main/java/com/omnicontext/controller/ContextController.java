package com.omnicontext.controller;

import com.omnicontext.dto.*;
import com.omnicontext.exception.ResourceNotFoundException;
import com.omnicontext.model.ContextCapsule;
import com.omnicontext.model.ContextCollaborator;
import com.omnicontext.service.ContextService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contexts")
public class ContextController {

    private final ContextService contextService;

    public ContextController(ContextService contextService) {
        this.contextService = contextService;
    }

    @GetMapping
    public ResponseEntity<List<ContextCapsule>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String project) {
        return ResponseEntity.ok(contextService.getAllContexts(search, project));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContextCapsule> getById(@PathVariable String id) {
        return contextService.getById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("ContextCapsule", id));
    }

    @PostMapping
    public ResponseEntity<ContextCapsule> create(@Valid @RequestBody CreateContextRequest request) {
        ContextCapsule created = contextService.createContext(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContextCapsule> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateContextRequest request) {
        ContextCapsule updated = contextService.updateContext(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        contextService.deleteContext(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/fork")
    public ResponseEntity<ContextCapsule> fork(@PathVariable String id) {
        ContextCapsule forked = contextService.forkContext(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(forked);
    }

    @PostMapping("/preview-compression")
    public ResponseEntity<CompressPreviewResponse> previewCompression(@RequestBody CompressPreviewRequest request) {
        return ResponseEntity.ok(contextService.previewCompression(request));
    }

    @GetMapping("/lmstudio-status")
    public ResponseEntity<java.util.Map<String, Object>> getLmStudioStatus() {
        return ResponseEntity.ok(contextService.getAiStatus());
    }

    @GetMapping("/{id}/notes")
    public ResponseEntity<List<ContextCollaborator>> getNotes(@PathVariable String id) {
        return ResponseEntity.ok(contextService.getNotes(id));
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<ContextCollaborator> addNote(
            @PathVariable String id,
            @Valid @RequestBody AddNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contextService.addNote(id, request));
    }
}
