-- Job tracking for async file processing.
-- Each upload creates a job that moves through: QUEUED → PROCESSING → COMPLETED/PARTIAL/FAILED

CREATE TABLE processing_jobs (
    id               UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    filename         VARCHAR(255)  NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    sql_file_path    VARCHAR(500),
    started_at       TIMESTAMP,
    completed_at     TIMESTAMP,
    created_at       TIMESTAMP     DEFAULT NOW() NOT NULL,
    last_updated_at  TIMESTAMP
);

CREATE INDEX idx_processing_jobs_status_last_updated ON processing_jobs (status, last_updated_at);
