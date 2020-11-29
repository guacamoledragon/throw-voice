create sequence aliases_id_seq owned by aliases.id;

select setval('aliases_id_seq', (select coalesce(max(id) + 1, 1) from aliases));

alter table aliases alter column id set default nextval('aliases_id_seq')
