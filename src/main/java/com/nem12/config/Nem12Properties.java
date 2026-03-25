package com.nem12.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "nem12")
public class Nem12Properties {

    @NotBlank(message = "API key must be configured. Set NEM12_API_KEY environment variable.")
    private String apiKey;

    @Min(value = 100, message = "Batch size must be at least 100")
    private int batchSize = 1000;

    @NotBlank(message = "SQL output directory must be configured")
    private String sqlOutputDirectory = "./output";

    @Min(value = 1, message = "Job timeout must be at least 1 minute")
    private int jobTimeoutMinutes = 30;

    @Min(value = 1, message = "Must have at least 1 batch worker")
    private int concurrentBatchWorkers = 4;

    @Min(value = 1, message = "Must allow at least 1 in-flight batch")
    private int maxInFlightBatches = 8;

    @Min(value = 60000, message = "Cleanup interval must be at least 60 seconds")
    private long cleanupIntervalMs = 300000;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getSqlOutputDirectory() {
        return sqlOutputDirectory;
    }

    public void setSqlOutputDirectory(String sqlOutputDirectory) {
        this.sqlOutputDirectory = sqlOutputDirectory;
    }

    public int getJobTimeoutMinutes() {
        return jobTimeoutMinutes;
    }

    public void setJobTimeoutMinutes(int jobTimeoutMinutes) {
        this.jobTimeoutMinutes = jobTimeoutMinutes;
    }

    public int getConcurrentBatchWorkers() {
        return concurrentBatchWorkers;
    }

    public void setConcurrentBatchWorkers(int concurrentBatchWorkers) {
        this.concurrentBatchWorkers = concurrentBatchWorkers;
    }

    public int getMaxInFlightBatches() {
        return maxInFlightBatches;
    }

    public void setMaxInFlightBatches(int maxInFlightBatches) {
        this.maxInFlightBatches = maxInFlightBatches;
    }

    public long getCleanupIntervalMs() {
        return cleanupIntervalMs;
    }

    public void setCleanupIntervalMs(long cleanupIntervalMs) {
        this.cleanupIntervalMs = cleanupIntervalMs;
    }
}
