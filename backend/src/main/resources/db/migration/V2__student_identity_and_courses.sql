ALTER TABLE app_account ADD CONSTRAINT app_account_id_role_key UNIQUE (id, role);

CREATE TABLE student_identity (
    account_id UUID PRIMARY KEY,
    account_role VARCHAR(20) NOT NULL DEFAULT 'STUDENT' CHECK (account_role = 'STUDENT'),
    student_id TEXT NOT NULL UNIQUE CHECK (length(student_id) > 0),
    student_name TEXT NOT NULL CHECK (length(student_name) > 0),
    FOREIGN KEY (account_id, account_role) REFERENCES app_account(id, role)
);

CREATE TABLE course (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name TEXT NOT NULL CHECK (length(name) > 0)
);
CREATE TABLE course_teacher_assignment (
    course_id UUID NOT NULL REFERENCES course(id),
    account_id UUID NOT NULL,
    account_role VARCHAR(20) NOT NULL DEFAULT 'TEACHER' CHECK (account_role = 'TEACHER'),
    PRIMARY KEY (course_id, account_id),
    FOREIGN KEY (account_id, account_role) REFERENCES app_account(id, role)
);
CREATE INDEX course_teacher_account_index ON course_teacher_assignment(account_id, course_id);
CREATE TABLE course_enrollment (
    course_id UUID NOT NULL REFERENCES course(id),
    account_id UUID NOT NULL REFERENCES student_identity(account_id),
    PRIMARY KEY (course_id, account_id)
);
CREATE INDEX course_enrollment_account_index ON course_enrollment(account_id, course_id);
