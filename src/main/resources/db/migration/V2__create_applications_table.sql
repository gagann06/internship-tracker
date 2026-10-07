CREATE TABLE applications (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL REFERENCES companies (id),
    role_title      TEXT NOT NULL,
    business_stream TEXT,
    applied_date    DATE,
    deadline        DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()    
);

CREATE INDEX applications_company_id_idx on applications (company_id);