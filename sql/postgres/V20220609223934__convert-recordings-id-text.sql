alter table recordings
alter column id type text using id::text;

alter table recordings
alter column id drop default;
