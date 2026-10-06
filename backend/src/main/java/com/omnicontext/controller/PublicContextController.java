package com.omnicontext.controller;

import com.omnicontext.model.ContextCapsule;
import com.omnicontext.service.ContextService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/c")
public class PublicContextController {

    private final ContextService contextService;

    public PublicContextController(ContextService contextService) {
        this.contextService = contextService;
    }

    /**
     * JSON payload for human dashboard or API clients
     */
    @GetMapping("/{slug}")
    public ResponseEntity<ContextCapsule> getPublicContext(@PathVariable String slug) {
        return contextService.getBySlug(slug)
                .filter(ContextCapsule::isPublic)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Raw text endpoint for AI web browsing (ChatGPT Web, Claude Search, Perplexity, curl)
     */
    @GetMapping(value = "/{slug}/raw", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getRawContext(@PathVariable String slug) {
        return contextService.getBySlug(slug)
                .filter(ContextCapsule::isPublic)
                .map(c -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + c.getShareSlug() + ".txt\"")
                        .body(c.getCompressedContent()))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Ready-to-inject LLM Prompt with behavioral instructions
     */
    @GetMapping(value = "/{slug}/prompt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getInjectionPrompt(@PathVariable String slug) {
        return contextService.getBySlug(slug)
                .filter(ContextCapsule::isPublic)
                .map(c -> {
                    String prompt = String.format("""
=== OMNICONTEXT ACTIVE MEMORY [%s | Project: %s | v%d] ===
%s
=== INSTRUCTIONS FOR ASSISTANT ===
1. Absorb the state, tech stack, active tasks, and decisions above as ground-truth memory.
2. Maintain strict consistency with the decisions and constraints recorded.
3. Acknowledge this context briefly in one sentence and ask or proceed with the active task.
""", c.getTitle(), c.getProject(), c.getVersion(), c.getCompressedContent());
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"prompt-" + c.getShareSlug() + ".txt\"")
                            .body(prompt);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Model Context Protocol (MCP) Resource representation
     */
    @GetMapping(value = "/{slug}/mcp", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getMcpResource(@PathVariable String slug) {
        return contextService.getBySlug(slug)
                .filter(ContextCapsule::isPublic)
                .map(c -> {
                    Map<String, Object> mcp = new LinkedHashMap<>();
                    mcp.put("uri", "context://" + c.getShareSlug());
                    mcp.put("name", c.getTitle());
                    mcp.put("description", "Synced AI context for project " + c.getProject());
                    mcp.put("mimeType", "text/plain");

                    Map<String, Object> content = new LinkedHashMap<>();
                    content.put("text", c.getCompressedContent());
                    content.put("tokens", c.getCompressedTokens());
                    content.put("version", c.getVersion());
                    mcp.put("contents", content);

                    return ResponseEntity.ok(mcp);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
