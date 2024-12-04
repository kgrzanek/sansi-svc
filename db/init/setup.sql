DROP SCHEMA public;
CREATE SCHEMA sansi;

-- Drop users if they exist (useful for resetting)
DROP USER IF EXISTS sansi_owner;

-- Create users with login capability
CREATE USER sansi_owner WITH PASSWORD '12345' LOGIN;

ALTER DATABASE sansi OWNER TO sansi_owner;

GRANT ALL PRIVILEGES ON             DATABASE sansi TO sansi_owner;
GRANT ALL PRIVILEGES ON               SCHEMA sansi TO sansi_owner;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA sansi TO sansi_owner;

ALTER USER sansi_owner SET search_path TO sansi;
