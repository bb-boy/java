package com.example.kafka.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 系统配置实体 - 阈值/规则/窗口参数
 */
@Entity
@Table(name = "system_config", indexes = {
    @Index(name = "idx_cfg_scope_time", columnList = "scope, updated_at")
})
public class SystemConfigEntity {

    private static final long DEFAULT_VERSION = 1L;

    @Id
    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "scope", nullable = false)
    private String scope;

    @Lob
    @Column(name = "config_value", columnDefinition = "TEXT", nullable = false)
    private String configValue;

    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "comment")
    private String comment;

    @PrePersist
    protected void onCreate() {
        if (version == null) {
            version = DEFAULT_VERSION;
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
