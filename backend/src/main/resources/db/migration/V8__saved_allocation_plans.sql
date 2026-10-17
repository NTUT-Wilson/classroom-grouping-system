CREATE INDEX grouping_activity_deadline ON grouping_activity(deadline, id);

CREATE TABLE allocation_work (
    activity_id UUID PRIMARY KEY REFERENCES grouping_activity(id),
    state VARCHAR(12) NOT NULL CHECK (state IN ('RETRY', 'HALTED', 'PREPARED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    retry_at TIMESTAMP WITH TIME ZONE,
    failure_class VARCHAR(20) CHECK (failure_class IN ('TRANSIENT', 'INTEGRITY')),
    CHECK ((state='RETRY' AND retry_at IS NOT NULL AND failure_class IS NOT NULL AND failure_class='TRANSIENT') OR
           (state='HALTED' AND retry_at IS NULL AND failure_class IS NOT NULL AND failure_class='INTEGRITY') OR
           (state='PREPARED' AND retry_at IS NULL AND failure_class IS NULL))
);

CREATE TABLE allocation_plan (
    activity_id UUID PRIMARY KEY REFERENCES grouping_activity(id),
    state VARCHAR(10) NOT NULL CHECK (state IN ('PREPARING', 'COMPLETE')),
    placement_count INTEGER NOT NULL CHECK (placement_count >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE allocation_placement (
    activity_id UUID NOT NULL REFERENCES allocation_plan(activity_id),
    account_id UUID NOT NULL,
    group_id UUID NOT NULL,
    PRIMARY KEY (activity_id, account_id),
    FOREIGN KEY (activity_id, account_id) REFERENCES activity_participant(activity_id, account_id),
    FOREIGN KEY (activity_id, group_id) REFERENCES activity_group(activity_id, id)
);

CREATE FUNCTION protect_allocation_plan() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.state = 'COMPLETE' THEN
        RAISE EXCEPTION 'A complete allocation plan is immutable' USING ERRCODE='23514';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER allocation_plan_immutable BEFORE UPDATE OR DELETE ON allocation_plan
    FOR EACH ROW EXECUTE FUNCTION protect_allocation_plan();

CREATE FUNCTION protect_allocation_placement() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE target UUID; plan_state VARCHAR(10);
BEGIN
    IF TG_OP = 'INSERT' THEN target := NEW.activity_id; ELSE target := OLD.activity_id; END IF;
    SELECT state INTO plan_state FROM allocation_plan WHERE activity_id=target FOR UPDATE;
    IF plan_state IS NULL OR plan_state='COMPLETE' OR
       (TG_OP='UPDATE' AND NEW.activity_id <> OLD.activity_id) THEN
        RAISE EXCEPTION 'A complete allocation placement is immutable' USING ERRCODE='23514';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER allocation_placement_immutable BEFORE INSERT OR UPDATE OR DELETE ON allocation_placement
    FOR EACH ROW EXECUTE FUNCTION protect_allocation_placement();

-- PREPARING is transaction-local: the complete header and all placements commit together.
CREATE FUNCTION require_complete_allocation_plan() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM allocation_plan p WHERE p.activity_id=NEW.activity_id AND
        (p.state <> 'COMPLETE' OR p.placement_count <>
            (SELECT count(*) FROM allocation_placement x WHERE x.activity_id=p.activity_id))) THEN
        RAISE EXCEPTION 'Allocation plan must commit complete' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER allocation_plan_complete AFTER INSERT OR UPDATE ON allocation_plan
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_complete_allocation_plan();
