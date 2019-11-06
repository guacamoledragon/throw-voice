-- Recordings that have been made in the last day
select datetime(r.created_on, '-8 hours')                                    as timestamp,
       round((julianday(modified_on) - julianday(r.created_on)) * 1440.0, 2) as duration,
       url,
       guilds.name                                                           as guild,
       channels.name                                                         as channel,
       guilds.region                                                         as region
from Recordings r
         join Guilds guilds on r.guild = guilds.id
         join Channels channels on r.channel = channels.id
where url notnull
  and julianday(r.created_on) between julianday('now', '-2 days') and julianday('now')
order by r.created_on desc;


select guilds.name as Guild,
       count(*)    as Count
from Recordings
       left join Guilds guilds on Recordings.guild = guilds.id
where Recordings.modified_on IS NULL
  and (julianday('now') - julianday(created_on)) < 0.02
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

select datetime(created_on, 'localtime') as created_on, name
from Guilds
where id = 333055724198559745
  and ifnull(created_on, '') not like '%Z';


-- Adjust time by adding 8 hours and formatting to proper format
update Guilds
set created_on = strftime('%Y-%m-%dT%H:%M:%fZ', datetime(created_on, '+8 hours'))
where id = 333055724198559745
  and ifnull(created_on, '') not like '%Z';


select *
from Guilds
where date(last_active_on)
  not between date('now', '-30 days') and date('now')
limit 50;

select region
from Guilds
where region not null
limit 10;
