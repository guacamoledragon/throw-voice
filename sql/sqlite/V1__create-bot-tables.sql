create table Guilds
(
  id   INTEGER primary key,
  name TEXT not null
);

create table Settings
(
  id                 INTEGER primary key,
  autoSave           BOOLEAN       default 0 not null,
  prefix             TEXT          default '!' not null,
  defaultTextChannel BIGINT,
  volume             DECIMAL(3, 2) default 0.8 not null,
  guild              BIGINT not null
    references Guilds
      on delete cascade
);

create table Aliases
(
  id       INTEGER primary key,
  name     TEXT   not null,
  alias    TEXT   not null,
  settings BIGINT not null
    references Settings
      on delete cascade
);

create table Channels
(
  id        INTEGER primary key,
  name      TEXT   not null,
  autoJoin  INT,
  autoLeave INT default 1 not null,
  settings  BIGINT not null
    references Settings
      on delete cascade
);

create unique index Settings_guild_unique
  on Settings (guild);

create table Users
(
  id       INTEGER primary key,
  name     TEXT   not null,
  settings BIGINT not null
    references Settings
      on delete cascade
);

create unique index Users_name_settings_unique
  on Users (name, settings);
