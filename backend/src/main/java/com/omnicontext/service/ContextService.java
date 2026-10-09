package com.omnicontext.service;

import com.omnicontext.dto.*;
import com.omnicontext.exception.PayloadTooLargeException;
import com.omnicontext.exception.ResourceNotFoundException;
import com.omnicontext.model.ContextCapsule;
import com.omnicontext.model.ContextCollaborator;
import com.omnicontext.repository.ContextCapsuleRepository;
import com.omnicontext.repository.ContextCollaboratorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ContextService {

    private final ContextCapsuleRepository repository;
    private final ContextCollaboratorRepository collaboratorRepository;
    private final ContextCompressionEngine compressionEngine;
    private final MongoTemplate mongoTemplate;
    private final SecureRandom random = new SecureRandom();
    private final long maxRawContentBytes;

    public ContextService(ContextCapsuleRepository repository,
                          ContextCollaboratorRepository collaboratorRepository,
                          ContextCompressionEngine compressionEngine,
                          MongoTemplate mongoTemplate,
                          @Value("${payload.max-raw-content-bytes:51200}") long maxRawContentBytes) {
        this.repository = repository;
        this.collaboratorRepository = collaboratorRepository;
        this.compressionEngine = compressionEngine;
        this.mongoTemplate = mongoTemplate;
        this.maxRawContentBytes = maxRawContentBytes;
    }

    public List<ContextCapsule> getAllContexts(String search, String project) {
        if (search != null && !search.trim().isEmpty()) {
            return repository.searchByKeyword(search.trim());
        }
        if (project != null && !project.trim().isEmpty() && !project.equalsIgnoreCase("all")) {
            return repository.findByProjectOrderByUpdatedAtDesc(project.trim());
        }
        return repository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    public Optional<ContextCapsule> getById(String id) {
        return repository.findById(id);
    }

    public Optional<ContextCapsule> getBySlug(String shareSlug) {
        return repository.findByShareSlug(shareSlug);
    }

    public ContextCapsule createContext(CreateContextRequest req) {
        // Payload size guard
        validatePayloadSize(req.getRawContent());

        ContextCapsule capsule = new ContextCapsule();
        capsule.setId(UUID.randomUUID().toString());
        capsule.setTitle(req.getTitle().trim());
        capsule.setProject((req.getProject() == null || req.getProject().isBlank()) ? "General" : req.getProject().trim());
        capsule.setDescription(req.getDescription());
        capsule.setRawContent(req.getRawContent());
        capsule.setTags(req.getTags() != null ? req.getTags().trim() : "");
        capsule.setPublic(req.getIsPublic() == null || req.getIsPublic());
        capsule.setShareSlug(generateUniqueSlug());
        capsule.setVersion(1);
        capsule.setCreatedAt(LocalDateTime.now());
        capsule.setUpdatedAt(LocalDateTime.now());

        String strategy = req.getCompressionStrategy() != null ? req.getCompressionStrategy() : "SEMANTIC_DENSE";
        capsule.setCompressionStrategy(strategy);

        // Run semantic compression engine
        ContextCompressionEngine.CompressionResult result = compressionEngine.compress(
                capsule.getTitle(), capsule.getProject(), capsule.getRawContent(), strategy
        );

        capsule.setCompressedContent(result.getCompressedContent());
        capsule.setOriginalTokens(result.getOriginalTokens());
        capsule.setCompressedTokens(result.getCompressedTokens());
        capsule.setCompressionRatio(result.getCompressionRatio());

        return repository.save(capsule);
    }

    public ContextCapsule updateContext(String id, UpdateContextRequest req) {
        ContextCapsule capsule = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ContextCapsule", id));

        boolean needsRecompression = false;

        if (req.getTitle() != null && !req.getTitle().isBlank()) {
            capsule.setTitle(req.getTitle().trim());
            needsRecompression = true;
        }
        if (req.getProject() != null && !req.getProject().isBlank()) {
            capsule.setProject(req.getProject().trim());
            needsRecompression = true;
        }
        if (req.getDescription() != null) {
            capsule.setDescription(req.getDescription());
        }
        if (req.getTags() != null) {
            capsule.setTags(req.getTags());
        }
        if (req.getIsPublic() != null) {
            capsule.setPublic(req.getIsPublic());
        }
        if (req.getCompressionStrategy() != null) {
            capsule.setCompressionStrategy(req.getCompressionStrategy());
            needsRecompression = true;
        }
        if (req.getRawContent() != null && !req.getRawContent().equals(capsule.getRawContent())) {
            validatePayloadSize(req.getRawContent());
            capsule.setRawContent(req.getRawContent());
            needsRecompression = true;
        }

        capsule.touch();

        if (needsRecompression) {
            capsule.setVersion(capsule.getVersion() + 1);
            ContextCompressionEngine.CompressionResult result = compressionEngine.compress(
                    capsule.getTitle(), capsule.getProject(), capsule.getRawContent(), capsule.getCompressionStrategy()
            );
            capsule.setCompressedContent(result.getCompressedContent());
            capsule.setOriginalTokens(result.getOriginalTokens());
            capsule.setCompressedTokens(result.getCompressedTokens());
            capsule.setCompressionRatio(result.getCompressionRatio());
        } else if (req.getCompressedContent() != null) {
            capsule.setCompressedContent(req.getCompressedContent());
            capsule.setCompressedTokens(compressionEngine.estimateTokens(req.getCompressedContent()));
            if (capsule.getOriginalTokens() > 0) {
                double ratio = ((double)(capsule.getOriginalTokens() - capsule.getCompressedTokens()) / capsule.getOriginalTokens()) * 100.0;
                capsule.setCompressionRatio(Math.round(ratio * 10.0) / 10.0);
            }
        }

        return repository.save(capsule);
    }

    public void deleteContext(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("ContextCapsule", id);
        }
        collaboratorRepository.deleteByContextId(id);
        repository.deleteById(id);
    }

    public int recompressAll() {
        List<ContextCapsule> all = repository.findAll();
        for (ContextCapsule c : all) {
            if (c.getRawContent() != null && !c.getRawContent().trim().isEmpty()) {
                var res = compressionEngine.compress(c.getTitle(), c.getProject(), c.getRawContent(), c.getCompressionStrategy());
                c.setCompressedContent(res.getCompressedContent());
                c.setOriginalTokens(res.getOriginalTokens());
                c.setCompressedTokens(res.getCompressedTokens());
                c.setCompressionRatio(res.getCompressionRatio());
                repository.save(c);
            }
        }
        return all.size();
    }

    public boolean isLmStudioAvailable() {
        return compressionEngine.isLmStudioAvailable();
    }

    public java.util.Map<String, Object> getAiStatus() {
        return compressionEngine.getAiStatus();
    }

    public ContextCapsule forkContext(String sourceId) {
        ContextCapsule source = repository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("ContextCapsule", sourceId));

        ContextCapsule forked = new ContextCapsule();
        forked.setId(UUID.randomUUID().toString());
        forked.setTitle("Fork: " + source.getTitle());
        forked.setProject(source.getProject());
        forked.setDescription("Forked from " + source.getTitle() + " (v" + source.getVersion() + ")");
        forked.setRawContent(source.getRawContent());
        forked.setCompressedContent(source.getCompressedContent());
        forked.setCompressionStrategy(source.getCompressionStrategy());
        forked.setOriginalTokens(source.getOriginalTokens());
        forked.setCompressedTokens(source.getCompressedTokens());
        forked.setCompressionRatio(source.getCompressionRatio());
        forked.setTags(source.getTags());
        forked.setPublic(source.isPublic());
        forked.setShareSlug(generateUniqueSlug());
        forked.setVersion(1);
        forked.setCreatedAt(LocalDateTime.now());
        forked.setUpdatedAt(LocalDateTime.now());

        return repository.save(forked);
    }

    public CompressPreviewResponse previewCompression(CompressPreviewRequest req) {
        String title = (req.getTitle() != null && !req.getTitle().isBlank()) ? req.getTitle() : "Untitled";
        String project = (req.getProject() != null && !req.getProject().isBlank()) ? req.getProject() : "General";
        String raw = req.getRawContent() != null ? req.getRawContent() : "";
        String strategy = req.getCompressionStrategy() != null ? req.getCompressionStrategy() : "SEMANTIC_DENSE";

        // Validate payload size for preview too
        validatePayloadSize(raw);

        ContextCompressionEngine.CompressionResult res = compressionEngine.compress(title, project, raw, strategy);
        return new CompressPreviewResponse(
                res.getCompressedContent(),
                res.getOriginalTokens(),
                res.getCompressedTokens(),
                res.getCompressionRatio(),
                res.getTokensSaved(),
                strategy
        );
    }

    public ContextCollaborator addNote(String contextId, AddNoteRequest req) {
        if (!repository.existsById(contextId)) {
            throw new ResourceNotFoundException("ContextCapsule", contextId);
        }
        ContextCollaborator note = new ContextCollaborator();
        note.setId(UUID.randomUUID().toString());
        note.setContextId(contextId);
        note.setAuthorName(req.getAuthorName() != null ? req.getAuthorName().trim() : "Collaborator");
        note.setNote(req.getNote().trim());
        note.setCreatedAt(LocalDateTime.now());
        return collaboratorRepository.save(note);
    }

    public List<ContextCollaborator> getNotes(String contextId) {
        return collaboratorRepository.findByContextIdOrderByCreatedAtDesc(contextId);
    }

    public StatsSummaryResponse getStats() {
        List<ContextCapsule> all = repository.findAll();
        long total = all.size();

        long totalTokensSaved = all.stream()
                .mapToLong(c -> Math.max(0, c.getOriginalTokens() - c.getCompressedTokens()))
                .sum();

        double avgRatio = all.isEmpty() ? 0.0 : all.stream()
                .mapToDouble(ContextCapsule::getCompressionRatio)
                .average()
                .orElse(0.0);

        List<String> projects = mongoTemplate.findDistinct("project", ContextCapsule.class, String.class);
        Collections.sort(projects);

        return new StatsSummaryResponse(
                total,
                totalTokensSaved,
                Math.round(avgRatio * 10.0) / 10.0,
                projects
        );
    }

    /**
     * Validates rawContent payload size against the configured maximum.
     * Prevents memory DOS and prompt injection abuse on large payloads.
     */
    private void validatePayloadSize(String rawContent) {
        if (rawContent != null) {
            long sizeBytes = rawContent.getBytes(StandardCharsets.UTF_8).length;
            if (sizeBytes > maxRawContentBytes) {
                throw new PayloadTooLargeException(sizeBytes, maxRawContentBytes);
            }
        }
    }

    private String generateUniqueSlug() {
        String chars = "abcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        String candidate = sb.toString();
        if (repository.findByShareSlug(candidate).isPresent()) {
            return generateUniqueSlug();
        }
        return candidate;
    }
}
