ALTER TABLE companies ADD COLUMN user_id BIGINT REFERENCES users (id);
ALTER TABLE applications ADD COLUMN user_id BIGINT REFERENCES users (id);

UPDATE companies SET user_id = (SELECT id FROM users ORDER BY id LIMIT 1);
UPDATE applications SET user_id =  (SELECT id FROM users ORDER BY id LIMIT 1);

ALTER TABLE companies ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE applications ALTER COLUMN user_id SET NOT NULL;

DROP INDEX companies_name_ci_key;
CREATE UNIQUE INDEX companies_user_name_ci_key ON companies (user_id, upper(name));

CREATE INDEX applications_user_id_idx ON applications (user_id);