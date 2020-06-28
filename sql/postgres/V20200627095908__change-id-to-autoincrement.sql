create
    sequence settings_id_seq;

alter table settings
    alter column id set default nextval('settings_id_seq');

alter
    sequence settings_id_seq owned by settings.id;

create
    sequence recordings_id_seq;

alter table recordings
    alter column id set default nextval('recordings_id_seq');

alter
    sequence recordings_id_seq owned by recordings.id;

