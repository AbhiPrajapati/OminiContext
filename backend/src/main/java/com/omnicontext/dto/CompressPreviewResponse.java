package com.omnicontext.dto;

public class CompressPreviewResponse {

    private String compressedContent;
    private int originalTokens;
    private int compressedTokens;
    private double compressionRatio;
    private int tokensSaved;
    private String strategyUsed;

    public CompressPreviewResponse() {
    }

    public CompressPreviewResponse(String compressedContent, int originalTokens, int compressedTokens,
                                   double compressionRatio, int tokensSaved, String strategyUsed) {
        this.compressedContent = compressedContent;
        this.originalTokens = originalTokens;
        this.compressedTokens = compressedTokens;
        this.compressionRatio = compressionRatio;
        this.tokensSaved = tokensSaved;
        this.strategyUsed = strategyUsed;
    }

    public String getCompressedContent() {
        return compressedContent;
    }

    public void setCompressedContent(String compressedContent) {
        this.compressedContent = compressedContent;
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

    public int getTokensSaved() {
        return tokensSaved;
    }

    public void setTokensSaved(int tokensSaved) {
        this.tokensSaved = tokensSaved;
    }

    public String getStrategyUsed() {
        return strategyUsed;
    }

    public void setStrategyUsed(String strategyUsed) {
        this.strategyUsed = strategyUsed;
    }
}
