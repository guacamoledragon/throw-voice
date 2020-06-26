-- Remove all channel rows that autoRecord = null and autoStop = 1
delete
from Channels
where autoStop = 1
  and autoRecord isnull;

--  Allow autostop to be null, just like autorecord
alter table Channels
  alter column autoStop drop default;

-- Reset autoStop to null to prevent accidental leaves
update Channels
set autoStop = null
where autoStop = 1;
