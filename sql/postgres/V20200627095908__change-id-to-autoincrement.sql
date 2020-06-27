create
sequence settings_id_seq;

alter table settings alter column id set default nextval('settings_id_seq');

alter
sequence settings_id_seq owned by settings.id;
