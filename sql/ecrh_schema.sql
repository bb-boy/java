CREATE TABLE IF NOT EXISTS protection_type_dict (
    protection_type_code VARCHAR(64) PRIMARY KEY,
    iter_name VARCHAR(128),
    display_name_zh VARCHAR(255),
    protection_group VARCHAR(128),
    equipment_scope VARCHAR(128),
    detection_mechanism VARCHAR(128),
    default_severity VARCHAR(32),
    default_action VARCHAR(128),
    authority_default VARCHAR(32),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS operation_mode_dict (
    operation_mode_code VARCHAR(64) PRIMARY KEY,
    display_name_zh VARCHAR(255),
    scope_note VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS operation_task_dict (
    operation_task_code VARCHAR(64) PRIMARY KEY,
    iter_name VARCHAR(128),
    display_name_zh VARCHAR(255),
    task_group VARCHAR(128),
    equipment_scope VARCHAR(128),
    allowed_mode_hint VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS operation_type_dict (
    operation_type_code VARCHAR(64) PRIMARY KEY,
    display_name_zh VARCHAR(255),
    operation_group VARCHAR(128),
    iter_control_function VARCHAR(128),
    requires_old_new BOOLEAN NOT NULL DEFAULT FALSE,
    requires_task_code BOOLEAN NOT NULL DEFAULT FALSE,
    requires_mode_code BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS tdms_artifacts (
    artifact_id VARCHAR(64) PRIMARY KEY,
    shot_no INT NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    file_name VARCHAR(255),
    file_size_bytes BIGINT,
    file_mtime DATETIME(3),
    sha256_hex VARCHAR(64) NOT NULL,
    artifact_status VARCHAR(32) NOT NULL,
    waveform_ingest_status VARCHAR(32),
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_tdms_artifacts_sha (sha256_hex)
);

CREATE TABLE IF NOT EXISTS source_records (
    source_record_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_system VARCHAR(64) NOT NULL,
    source_entity_type VARCHAR(64),
    source_entity_id VARCHAR(128),
    topic_name VARCHAR(128) NOT NULL,
    partition_id INT NOT NULL,
    offset_value BIGINT NOT NULL,
    message_key VARCHAR(256),
    payload_hash VARCHAR(64),
    produced_at DATETIME(3),
    consumed_at DATETIME(3) NOT NULL,
    ingest_status VARCHAR(32) NOT NULL,
    raw_payload_json JSON,
    UNIQUE KEY uk_source_records_offset (topic_name, partition_id, offset_value)
);

CREATE TABLE IF NOT EXISTS signal_catalog (
    process_id VARCHAR(128) PRIMARY KEY,
    display_name VARCHAR(255),
    signal_class VARCHAR(64),
    unit VARCHAR(32),
    value_type VARCHAR(32),
    device_scope VARCHAR(64),
    is_key_signal BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS signal_source_map (
    map_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_system VARCHAR(64) NOT NULL,
    source_type VARCHAR(64),
    source_name VARCHAR(128) NOT NULL,
    process_id VARCHAR(128) NOT NULL,
    data_type VARCHAR(32),
    is_primary_waveform BOOLEAN NOT NULL DEFAULT FALSE,
    is_primary_operation_signal BOOLEAN NOT NULL DEFAULT FALSE,
    is_primary_protection_signal BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE KEY uk_signal_source (source_system, source_type, source_name, data_type)
);

CREATE TABLE IF NOT EXISTS shots (
    shot_no INT PRIMARY KEY,
    shot_start_time DATETIME(3),
    shot_end_time DATETIME(3),
    expected_duration DOUBLE,
    actual_duration DOUBLE,
    status_code VARCHAR(32),
    status_reason VARCHAR(255),
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS events (
    event_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shot_no INT NOT NULL,
    source_record_id BIGINT,
    artifact_id VARCHAR(64),
    event_family VARCHAR(32) NOT NULL,
    event_code VARCHAR(64),
    event_name VARCHAR(255),
    event_time DATETIME(3) NOT NULL,
    process_id VARCHAR(128),
    message_text TEXT,
    message_level VARCHAR(32),
    severity VARCHAR(32),
    source_system VARCHAR(64) NOT NULL,
    authority_level VARCHAR(32),
    event_status VARCHAR(32),
    correlation_key VARCHAR(128),
    dedup_key VARCHAR(256) NOT NULL,
    raw_payload_json JSON,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_events_dedup (dedup_key),
    KEY idx_events_shot_time (shot_no, event_time),
    KEY idx_events_family_time (event_family, event_time)
);

CREATE TABLE IF NOT EXISTS event_operation_detail (
    event_id BIGINT PRIMARY KEY,
    operation_type_code VARCHAR(64),
    operation_mode_code VARCHAR(64),
    operation_task_code VARCHAR(64),
    channel_name VARCHAR(128),
    old_value DOUBLE,
    new_value DOUBLE,
    delta_value DOUBLE,
    command_name VARCHAR(128),
    command_params_json JSON,
    operator_id VARCHAR(64),
    execution_status VARCHAR(32),
    confidence DOUBLE
);

CREATE TABLE IF NOT EXISTS event_protection_detail (
    event_id BIGINT PRIMARY KEY,
    protection_type_code VARCHAR(64),
    protection_scope VARCHAR(64),
    trigger_condition VARCHAR(255),
    measured_value DOUBLE,
    threshold_value DOUBLE,
    threshold_op VARCHAR(16),
    action_taken VARCHAR(128),
    action_latency_us BIGINT,
    window_start DATETIME(3),
    window_end DATETIME(3),
    related_channels JSON,
    evidence_score DOUBLE,
    ack_state VARCHAR(32),
    ack_user_id VARCHAR(64),
    ack_time DATETIME(3)
);
