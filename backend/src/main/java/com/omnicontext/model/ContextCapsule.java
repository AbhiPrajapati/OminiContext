package com.omnicontext.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "context_capsules")
public class ContextCapsule {

    @Id
    private String id;

    private String title;

    @Indexed
    private String project;

    private String description;

    private String rawContent;

    private String compressedContent;

    private String compressionStrategy;

    private int originalTokens;

    private int compressedTokens;

    private double compressionRatio;

    private String tags;

    @Indexed(unique = true)
    private String shareSlug;

    private boolean isPublic = true;

    private int version = 1;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    public ContextCapsule() {
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public String getCompressedContent() {
        return compressedContent;
    }

    public void setCompressedContent(String compressedContent) {
        this.compressedContent = compressedContent;
    }

    public String getCompressionStrategy() {
        return compressionStrategy;
    }

    public void setCompressionStrategy(String compressionStrategy) {
        this.compressionStrategy = compressionStrategy;
    }

    public int getOriginalTokens() {
        return originalTokens;
    }

    public void setOriginalTokens(int originalTokens) {
        this.originalTokens = originalTokens;
    }

    public int getCompressedTokens() {
        return compressedTokens;
    }

    public void setCompressedTokens(int compressedTokens) {
        this.compressedTokens = compressedTokens;
    }

    public double getCompressionRatio() {
        return compressionRatio;
    }

    public void setCompressionRatio(double compressionRatio) {
        this.compressionRatio = compressionRatio;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getShareSlug() {
        return shareSlug;
    }

    public void setShareSlug(String shareSlug) {
        this.shareSlug = shareSlug;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
