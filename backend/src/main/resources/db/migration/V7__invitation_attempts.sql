CREATE TABLE invitation_attempt (
    id UUID PRIMARY KEY,
    activity_id UUID NOT NULL,
    group_id UUID NOT NULL,
    recipient_account_id UUID NOT NULL,
    sender_account_id UUID NOT NULL,
    status VARCHAR(10) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELED','EXPIRED')),
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (activity_id, group_id) REFERENCES activity_group(activity_id, id),
    FOREIGN KEY (activity_id, recipient_account_id) REFERENCES activity_participant(activity_id, account_id),
    FOREIGN KEY (activity_id, group_id, sender_account_id)
        REFERENCES activity_membership(activity_id, group_id, account_id),
    CHECK (recipient_account_id <> sender_account_id),
    CHECK ((status = 'CANCELED' AND reason IS NOT NULL AND reason IN ('Joined a group','Group full')) OR (status <> 'CANCELED' AND reason IS NULL))
);
CREATE UNIQUE INDEX invitation_pending_pair ON invitation_attempt(activity_id, group_id, recipient_account_id) WHERE status='PENDING';
CREATE INDEX invitation_recipient_history ON invitation_attempt(activity_id, recipient_account_id, created_at DESC, id DESC);
CREATE INDEX invitation_sender_history ON invitation_attempt(activity_id, sender_account_id, created_at DESC, id DESC);
