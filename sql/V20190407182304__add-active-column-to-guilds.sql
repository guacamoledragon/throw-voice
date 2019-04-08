-- Remove Guilds that never connected since the new year
delete
from Guilds
where created_on = '2019-01-01 00:00:00'
  and last_active_on = '2019-01-01 00:00:00';

alter table Guilds
  add active TEXT default 'true';
