drop schema if exists public cascade;
create schema sansi;

-- create owner user with all privileges
drop user if exists sansi_owner;
create user sansi_owner with password '12345' login;

-- grant all necessary privileges in one step
alter database sansi owner to sansi_owner;
grant all privileges on schema sansi to sansi_owner;
grant all privileges on all tables in schema sansi to sansi_owner;
grant usage on schema sansi to sansi_owner;
alter default privileges in schema sansi grant all on tables to sansi_owner;

-- set search path
alter user sansi_owner set search_path to sansi;
