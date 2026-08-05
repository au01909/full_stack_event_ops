CREATE TABLE tasks (
    id                      BIGSERIAL PRIMARY KEY,
    name                    VARCHAR(150)  NOT NULL,
    description             VARCHAR(2000),
    task_type               VARCHAR(50)   NOT NULL,
    priority                VARCHAR(20)   NOT NULL,
    status                  VARCHAR(20)   NOT NULL,
    input_duration_seconds  INTEGER,
    input_data              VARCHAR(4000),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    started_at              TIMESTAMP WITH TIME ZONE,
    completed_at            TIMESTAMP WITH TIME ZONE,
    execution_duration_ms   BIGINT,
    error_message           VARCHAR(2000),
    retry_count             INTEGER       NOT NULL DEFAULT 0,
    version                 BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT chk_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_tasks_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_tasks_status ON tasks (status);
CREATE INDEX idx_tasks_priority ON tasks (priority);
CREATE INDEX idx_tasks_created_at ON tasks (created_at DESC);
