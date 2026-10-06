package com.omnicontext.dto;

import jakarta.validation.constraints.Size;

public class UpdateContextRequest {

    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;

    private String project;

    private String description;

    private String rawContent;

    private String compressedContent;

    private String compressionStrategy;

    private String tags;

    private Boolean isPublic;

    public UpdateContextRequest() {
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

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }
}
