CREATE TABLE course_introduction (
    course_id UUID NOT NULL,
    account_id UUID NOT NULL,
    about_me TEXT NOT NULL CHECK (char_length(about_me) <= 500),
    PRIMARY KEY (course_id, account_id),
    FOREIGN KEY (course_id, account_id) REFERENCES course_enrollment(course_id, account_id)
);
