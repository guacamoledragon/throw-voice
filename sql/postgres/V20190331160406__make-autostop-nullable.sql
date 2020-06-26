-- Remove all channel rows that autoRecord = null and autoStop = true
delete
from Channels
where autoStop is true
  and autoRecord isnull;

--  Allow autostop to be null, just like autorecord
alter table Channels
  alter column autoStop drop default;

-- Reset autoStop to null to prevent accidental leaves
update Channels
set autoStop = null
where autoStop is true;
