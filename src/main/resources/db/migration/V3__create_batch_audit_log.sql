-- Batch audit log: per-batch processing details for each job

CREATE TABLE batch_audit_log (
    id                 UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    job_id             UUID NOT NULL REFERENCES processing_jobs(id) ON DELETE CASCADE,
    batch_number       INTEGER NOT NULL,
    reading_count      INTEGER NOT NULL,
    readings_persisted INTEGER NOT NULL DEFAULT 0,
    status             VARCHAR(10) NOT NULL,
    error_message      TEXT,
    created_at         TIMESTAMP DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_batch_audit_log_job_id ON batch_audit_log (job_id);
CREATE INDEX idx_batch_audit_log_job_status ON batch_audit_log (job_id, status);
