DROP SCHEMA public;
CREATE SCHEMA telsos;

-- Drop users if they exist (useful for resetting)
DROP USER IF EXISTS telsos_owner;

-- Create users with login capability
CREATE USER telsos_owner WITH PASSWORD 'telsos_owner_12345' LOGIN;

ALTER DATABASE telsos OWNER TO telsos_owner;

GRANT ALL PRIVILEGES ON DATABASE telsos TO telsos_owner;
GRANT ALL PRIVILEGES ON SCHEMA telsos TO telsos_owner;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA telsos TO telsos_owner;

ALTER USER telsos_owner SET search_path TO telsos;
