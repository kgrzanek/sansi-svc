drop schema if exists public cascade;
create schema telsos;

-- create owner user with all privileges
drop user if exists telsos_owner;
create user telsos_owner with password '12345' login;

-- grant all necessary privileges in one step
alter database telsos owner to telsos_owner;
grant all privileges on schema telsos to telsos_owner;
grant all privileges on all tables in schema telsos to telsos_owner;
grant usage on schema telsos to telsos_owner;
alter default privileges in schema telsos grant all on tables to telsos_owner;

-- set search path
alter user telsos_owner set search_path to telsos;
