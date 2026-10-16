ALTER TABLE grouping_activity ADD COLUMN finalized_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE allocation_work ADD COLUMN stage VARCHAR(12) NOT NULL DEFAULT 'PREPARATION'
    CHECK (stage IN ('PREPARATION','ASSIGNMENT'));
UPDATE allocation_work SET stage='ASSIGNMENT' WHERE state='PREPARED';
ALTER TABLE allocation_work DROP CONSTRAINT allocation_work_state_check;
ALTER TABLE allocation_work DROP CONSTRAINT allocation_work_check;
ALTER TABLE allocation_work ADD CHECK (state IN ('RETRY','HALTED','PREPARED','COMPLETE'));
ALTER TABLE allocation_work ADD CHECK (
    (state='RETRY' AND retry_at IS NOT NULL AND failure_class IS NOT NULL AND failure_class='TRANSIENT') OR
    (state='HALTED' AND retry_at IS NULL AND failure_class IS NOT NULL AND failure_class='INTEGRITY') OR
    (state IN ('PREPARED','COMPLETE') AND retry_at IS NULL AND failure_class IS NULL));
ALTER TABLE allocation_work ADD CHECK (state NOT IN ('PREPARED','COMPLETE') OR stage='ASSIGNMENT');
