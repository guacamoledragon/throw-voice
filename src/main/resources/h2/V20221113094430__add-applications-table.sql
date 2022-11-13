create table applications
(
  id         bigint,
  created_on timestamp with time zone
);

comment on table applications is 'Allows multiple bot instances to use the same database.';

comment on column applications.id is 'Discord Application ID';

comment on column applications.created_on is 'When this entry was created.';

create unique index applications_id_uindex
  on applications (id);
