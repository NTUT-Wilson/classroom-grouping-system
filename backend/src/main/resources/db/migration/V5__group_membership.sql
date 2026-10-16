CREATE TABLE activity_membership (
    activity_id UUID NOT NULL,
    account_id UUID NOT NULL,
    group_id UUID NOT NULL,
    PRIMARY KEY (activity_id, account_id),
    UNIQUE (activity_id, group_id, account_id),
    FOREIGN KEY (activity_id, account_id) REFERENCES activity_participant(activity_id, account_id),
    FOREIGN KEY (activity_id, group_id) REFERENCES activity_group(activity_id, id)
);

ALTER TABLE activity_group ADD COLUMN manager_account_id UUID;
ALTER TABLE activity_group ADD CONSTRAINT group_manager_is_member
    FOREIGN KEY (activity_id, id, manager_account_id)
    REFERENCES activity_membership(activity_id, group_id, account_id)
    DEFERRABLE INITIALLY DEFERRED;
