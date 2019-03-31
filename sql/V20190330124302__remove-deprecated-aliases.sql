-- Remove stop alias
delete
from Aliases
where name = 'LEAVE'
  and alias = 'stop';

-- Remove info alias
delete
from Aliases
where name = 'HELP'
  and alias = 'info';

-- Remove record alias
delete
from Aliases
where name = 'JOIN'
  and alias = 'record';

-- Remove symbol alias
delete
from Aliases
where name = 'PREFIX'
  and alias = 'symbol';

-- Remove alias alias, invalid alias
delete
from Aliases
where name = 'ALIAS';

-- Rename LEAVE -> STOP
update Aliases
set name = 'STOP'
where name = 'LEAVE'
  and not alias = 'stop';

-- Rename JOIN -> RECORD
update Aliases
set name = 'RECORD'
where name = 'JOIN'
  and not alias = 'record';

-- Rename AUTOLEAVE -> AUTOSTOP
update Aliases
set name = 'AUTOSTOP'
where name = 'AUTOLEAVE';

-- Rename AUTOJOIN -> AUTORECORD
update Aliases
set name = 'AUTORECORD'
where name = 'AUTOJOIN';

-- Remove redundant aliases
delete
from Aliases
where name = upper(alias);
