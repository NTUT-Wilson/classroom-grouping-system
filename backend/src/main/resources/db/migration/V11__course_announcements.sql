-- All owner operations serialize on course, then receipt/source/outbox. Content never enters receipts/events.
CREATE TABLE course_announcement (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES course(id),
    title TEXT,
    body TEXT,
    published_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    revision BIGINT NOT NULL CHECK (revision > 0),
    deleted_at TIMESTAMPTZ,
    CHECK ((deleted_at IS NOT NULL AND title IS NULL AND body IS NULL) OR
      (deleted_at IS NULL AND title IS NOT NULL AND body IS NOT NULL
       AND char_length(title) BETWEEN 1 AND 150 AND char_length(body) BETWEEN 1 AND 10000
       AND btrim(title, U&'\0009\000a\000b\000c\000d\001c\001d\001e\001f\0020\0085\00a0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200a\2028\2029\202f\205f\3000\feff') <> ''
       AND btrim(body, U&'\0009\000a\000b\000c\000d\001c\001d\001e\001f\0020\0085\00a0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200a\2028\2029\202f\205f\3000\feff') <> '')),
    UNIQUE (course_id, id)
);
CREATE INDEX course_announcement_active_page ON course_announcement(course_id, published_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE TABLE course_announcement_catalog (
    course_id UUID PRIMARY KEY REFERENCES course(id),
    revision BIGINT NOT NULL CHECK (revision > 0)
);
CREATE TABLE course_announcement_receipt (
    actor_id UUID NOT NULL REFERENCES app_account(id),
    course_id UUID NOT NULL REFERENCES course(id),
    command VARCHAR(6) NOT NULL CHECK(command IN ('CREATE','EDIT','DELETE')),
    command_key UUID NOT NULL,
    fingerprint CHAR(64) NOT NULL CHECK(fingerprint ~ '^[0-9a-f]{64}$'),
    source_id UUID NOT NULL,
    revision BIGINT NOT NULL CHECK(revision > 0),
    deleted BOOLEAN NOT NULL,
    PRIMARY KEY(actor_id, command, command_key),
    FOREIGN KEY(course_id,source_id) REFERENCES course_announcement(course_id,id) DEFERRABLE INITIALLY DEFERRED
);
CREATE TABLE course_announcement_outbox (
    event_id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    source_id UUID NOT NULL,
    revision BIGINT NOT NULL CHECK(revision > 0),
    change_kind VARCHAR(6) NOT NULL CHECK(change_kind IN ('CREATE','EDIT','DELETE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    acknowledged_at TIMESTAMPTZ,
    FOREIGN KEY(course_id,source_id) REFERENCES course_announcement(course_id,id),
    UNIQUE(source_id,revision)
);
CREATE INDEX course_announcement_outbox_pending ON course_announcement_outbox(created_at,event_id) WHERE acknowledged_at IS NULL;
CREATE INDEX course_announcement_outbox_course_compaction ON course_announcement_outbox(course_id,created_at,event_id);
