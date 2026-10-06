package com.omnicontext.dto;

import java.util.List;

public class StatsSummaryResponse {

    private long totalContexts;
    private long totalTokensSaved;
    private double averageCompressionRatio;
    private List<String> projects;

    public StatsSummaryResponse() {
    }

    public StatsSummaryResponse(long totalContexts, long totalTokensSaved, double averageCompressionRatio, List<String> projects) {
        this.totalContexts = totalContexts;
        this.totalTokensSaved = totalTokensSaved;
        this.averageCompressionRatio = averageCompressionRatio;
        this.projects = projects;
    }

    public long getTotalContexts() {
        return totalContexts;
    }

    public void setTotalContexts(long totalContexts) {
        this.totalContexts = totalContexts;
    }

    public long getTotalTokensSaved() {
        return totalTokensSaved;
    }

    public void setTotalTokensSaved(long totalTokensSaved) {
        this.totalTokensSaved = totalTokensSaved;
    }

    public double getAverageCompressionRatio() {
        return averageCompressionRatio;
    }

    public void setAverageCompressionRatio(double averageCompressionRatio) {
        this.averageCompressionRatio = averageCompressionRatio;
    }

    public List<String> getProjects() {
        return projects;
    }

    public void setProjects(List<String> projects) {
        this.projects = projects;
    }
}
