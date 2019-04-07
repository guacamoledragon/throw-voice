drop table Users;

create table Users
(
  id              INTEGER primary key,
  name            TEXT not null,
  voted_on        TEXT,
  supporter       TEXT,
  activated_guild BIGINT references Guilds
);
