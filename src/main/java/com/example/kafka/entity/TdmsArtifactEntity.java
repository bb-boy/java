package com.example.kafka.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tdms_artifacts")
public class TdmsArtifactEntity {
    @Id
    @Column(name = "artifact_id", nullable = false, length = 64)
    private String artifactId;

    @Column(name = "shot_no", nullable = false)
    private Integer shotNo;

    @Column(name = "data_type", nullable = false, length = 32)
    private String dataType;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "file_mtime")
    private LocalDateTime fileMtime;

    @Column(name = "sha256_hex", nullable = false, length = 64)
    private String sha256Hex;

    @Column(name = "artifact_status", nullable = false, length = 32)
    private String artifactStatus;

    @Column(name = "waveform_ingest_status", length = 32)
    private String waveformIngestStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public LocalDateTime getFileMtime() {
        return fileMtime;
    }

    public void setFileMtime(LocalDateTime fileMtime) {
        this.fileMtime = fileMtime;
    }

    public String getSha256Hex() {
        return sha256Hex;
    }

    public void setSha256Hex(String sha256Hex) {
        this.sha256Hex = sha256Hex;
    }

    public String getArtifactStatus() {
        return artifactStatus;
    }

    public void setArtifactStatus(String artifactStatus) {
        this.artifactStatus = artifactStatus;
    }

    public String getWaveformIngestStatus() {
        return waveformIngestStatus;
    }

    public void setWaveformIngestStatus(String waveformIngestStatus) {
        this.waveformIngestStatus = waveformIngestStatus;
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
