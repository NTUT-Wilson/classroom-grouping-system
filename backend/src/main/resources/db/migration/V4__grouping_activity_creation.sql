CREATE TABLE grouping_activity (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL UNIQUE REFERENCES course(id),
    instructions TEXT NOT NULL CHECK (char_length(instructions) BETWEEN 1 AND 5000 AND
        btrim(instructions, U&'\0009\000a\000b\000c\000d\001c\001d\001e\001f\0020\0085\00a0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200a\2028\2029\202f\205f\3000\feff') <> ''),
    deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (id, course_id)
);

-- A participant references the stable student identity, not the mutable enrollment relation.
CREATE TABLE activity_participant (
    activity_id UUID NOT NULL REFERENCES grouping_activity(id),
    account_id UUID NOT NULL REFERENCES student_identity(account_id),
    PRIMARY KEY (activity_id, account_id)
);

CREATE TABLE activity_group (
    id UUID PRIMARY KEY,
    activity_id UUID NOT NULL REFERENCES grouping_activity(id),
    ordinal INTEGER NOT NULL CHECK (ordinal >= 1),
    capacity INTEGER NOT NULL CHECK (capacity >= 2),
    UNIQUE (activity_id, id),
    UNIQUE (activity_id, ordinal)
);

CREATE TABLE grouping_command_receipt (
    actor_id UUID NOT NULL REFERENCES app_account(id),
    command VARCHAR(50) NOT NULL,
    command_key UUID NOT NULL,
    fingerprint TEXT NOT NULL,
    activity_id UUID NOT NULL REFERENCES grouping_activity(id) DEFERRABLE INITIALLY DEFERRED,
    PRIMARY KEY (actor_id, command, command_key)
);
