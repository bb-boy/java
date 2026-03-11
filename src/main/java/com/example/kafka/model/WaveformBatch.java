package com.example.kafka.model;

import java.time.Instant;
import java.util.List;

public class WaveformBatch {
    private String artifactId;
    private Integer shotNo;
    private String dataType;
    private String sourceSystem;
    private String channelName;
    private String processId;
    private Double sampleRateHz;
    private Integer chunkIndex;
    private Integer chunkCount;
    private Integer chunkStartIndex;
    private Instant windowStart;
    private Instant windowEnd;
    private String encoding;
    private List<Double> samples;

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public Integer getShotNo() {
        return shotNo;
    }

    public void setShotNo(Integer shotNo) {
        this.shotNo = shotNo;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public Double getSampleRateHz() {
        return sampleRateHz;
    }

    public void setSampleRateHz(Double sampleRateHz) {
        this.sampleRateHz = sampleRateHz;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public Integer getChunkStartIndex() {
        return chunkStartIndex;
    }

    public void setChunkStartIndex(Integer chunkStartIndex) {
        this.chunkStartIndex = chunkStartIndex;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(Instant windowEnd) {
        this.windowEnd = windowEnd;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public List<Double> getSamples() {
        return samples;
    }

    public void setSamples(List<Double> samples) {
        this.samples = samples;
    }
}
