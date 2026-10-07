ALTER TABLE applications
    ADD COLUMN status TEXT NOT NULL DEFAULT 'TO_APPLY'
        CHECK (status IN ('TO_APPLY', 'APPLIED',
'ONLINE_ASSESSMENT', 'ONLINE_ASSESSMENT_COMPLETED',
'HIREVUE', 'HIREVUE_COMPLETED',
'TELEPHONE_INTERVIEW', 'TELEPHONE_INTERVIEW_COMPLETED',
'VIDEO_INTERVIEW', 'VIDEO_INTERVIEW_COMPLETED',
'ASSESSMENT_CENTRE', 'ASSESSMENT_CENTRE_COMPLETED',
'OFFER', 'REJECTED', 'WITHDRAWN', 'EXPIRED'));

CREATE TABLE status_changes (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id  BIGINT NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    from_status     TEXT CHECK (from_status IN ('TO_APPLY', 'APPLIED',
                    'ONLINE_ASSESSMENT', 'ONLINE_ASSESSMENT_COMPLETED',
                    'HIREVUE', 'HIREVUE_COMPLETED',
                    'TELEPHONE_INTERVIEW', 'TELEPHONE_INTERVIEW_COMPLETED',
                    'VIDEO_INTERVIEW', 'VIDEO_INTERVIEW_COMPLETED',
                    'ASSESSMENT_CENTRE', 'ASSESSMENT_CENTRE_COMPLETED',
                    'OFFER', 'REJECTED', 'WITHDRAWN', 'EXPIRED')),
    to_status       TEXT NOT NULL CHECK (to_status IN ('TO_APPLY', 'APPLIED',
                    'ONLINE_ASSESSMENT', 'ONLINE_ASSESSMENT_COMPLETED',
                    'HIREVUE', 'HIREVUE_COMPLETED',
                    'TELEPHONE_INTERVIEW', 'TELEPHONE_INTERVIEW_COMPLETED',
                    'VIDEO_INTERVIEW', 'VIDEO_INTERVIEW_COMPLETED',
                    'ASSESSMENT_CENTRE', 'ASSESSMENT_CENTRE_COMPLETED',
                    'OFFER', 'REJECTED', 'WITHDRAWN', 'EXPIRED')),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    note            TEXT
);

CREATE INDEX status_applications_idx on status_changes (application_id);

INSERT INTO status_changes (application_id, from_status, to_status, changed_at)
SELECT id, NULL, status, created_at FROM applications;