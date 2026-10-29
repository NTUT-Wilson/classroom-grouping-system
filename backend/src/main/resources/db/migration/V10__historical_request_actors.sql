-- Request actors are immutable historical facts; membership contains only current members.
-- PostgreSQL truncates generated multi-column names, so identify the V1 FKs by
-- their referenced relation rather than relying on an inferred generated name.
DO $$
DECLARE historical_fk TEXT;
BEGIN
    SELECT conname INTO STRICT historical_fk FROM pg_constraint
        WHERE conrelid='application_attempt'::regclass AND contype='f'
            AND confrelid='activity_membership'::regclass;
    EXECUTE format('ALTER TABLE application_attempt DROP CONSTRAINT %I', historical_fk);
    SELECT conname INTO STRICT historical_fk FROM pg_constraint
        WHERE conrelid='invitation_attempt'::regclass AND contype='f'
            AND confrelid='activity_membership'::regclass;
    EXECUTE format('ALTER TABLE invitation_attempt DROP CONSTRAINT %I', historical_fk);
END $$;

ALTER TABLE application_attempt ADD CONSTRAINT application_manager_is_participant
    FOREIGN KEY (activity_id, manager_account_id)
    REFERENCES activity_participant(activity_id, account_id);
ALTER TABLE invitation_attempt ADD CONSTRAINT invitation_sender_is_participant
    FOREIGN KEY (activity_id, sender_account_id)
    REFERENCES activity_participant(activity_id, account_id);

ALTER TABLE application_attempt DROP CONSTRAINT application_attempt_check1;
ALTER TABLE application_attempt ADD CONSTRAINT application_cancellation_reason
    CHECK ((status = 'CANCELED' AND reason IS NOT NULL AND reason IN ('Joined a group','Group full','Group empty'))
        OR (status <> 'CANCELED' AND reason IS NULL));
ALTER TABLE invitation_attempt DROP CONSTRAINT invitation_attempt_check1;
ALTER TABLE invitation_attempt ADD CONSTRAINT invitation_cancellation_reason
    CHECK ((status = 'CANCELED' AND reason IS NOT NULL AND reason IN ('Joined a group','Group full','Group empty'))
        OR (status <> 'CANCELED' AND reason IS NULL));

CREATE INDEX application_group_history ON application_attempt(activity_id, group_id, created_at DESC, id DESC);
CREATE INDEX invitation_group_history ON invitation_attempt(activity_id, group_id, created_at DESC, id DESC);
