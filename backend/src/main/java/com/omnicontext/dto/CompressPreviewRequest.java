package com.omnicontext.dto;

public class CompressPreviewRequest {

    private String title;
    private String project;
    private String rawContent;
    private String compressionStrategy = "SEMANTIC_DENSE";

    public CompressPreviewRequest() {
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

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public String getCompressionStrategy() {
        return compressionStrategy;
    }

    public void setCompressionStrategy(String compressionStrategy) {
        this.compressionStrategy = compressionStrategy;
    }
}
