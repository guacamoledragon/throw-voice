update Recordings
set created_on = strftime('%Y-%m-%dT%H:%M:%fZ', datetime(created_on, '+8 hours'))
where created_on not like '%Z';

update Recordings
set modified_on = strftime('%Y-%m-%dT%H:%M:%fZ', datetime(modified_on, '+8 hours'))
where modified_on not null
  and modified_on not like '%Z';
