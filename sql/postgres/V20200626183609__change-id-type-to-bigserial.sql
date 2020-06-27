alter table channels
    alter column id type bigint using id::bigint;

alter table guilds
    alter column id type bigint using id::bigint;

alter table recordings
    alter column channel type bigint using channel::bigint,
    alter column guild type bigint using guild::bigint;

alter table settings
    alter column guild type bigint using guild::bigint;
