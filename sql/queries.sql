-- Recordings that have been made in the last day
select datetime(created_on)                                                as timestamp,
       round((julianday(modified_on) - julianday(created_on)) * 1440.0, 2) as duration,
       url,
       guilds.name                                                         as guild,
       channels.name                                                       as channel
from Recordings
       join Guilds guilds on Recordings.guild = guilds.id
       join Channels channels on Recordings.channel = channels.id
where url notnull
  and (julianday('now', 'localtime') - julianday(created_on)) < 1.12
order by created_on desc;


select guilds.name as Guild,
       count(*)    as Count
from Recordings
       left join Guilds guilds on Recordings.guild = guilds.id
where Recordings.modified_on IS NULL
  and (julianday('now') - julianday(created_on)) < 1.5
order by Count desc;


select guilds.name                                                                      as guild,
       printf('%05.2f', sum((julianday(modified_on) - julianday(created_on)) * 1440.0)) as recorded_minutes,
       count(*)                                                                         as recordings
from Recordings
       join Guilds guilds on Recordings.guild = guilds.id
       join Channels channels on Recordings.channel = channels.id
where url not null
  and (julianday('now') - julianday(created_on)) < 1.5
group by guilds.name
having recorded_minutes > 1
order by recorded_minutes desc;
