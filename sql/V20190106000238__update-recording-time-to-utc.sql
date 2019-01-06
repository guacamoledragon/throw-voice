update Recordings
set created_on = datetime(created_on, '+8 hours');

update Recordings
set modified_on = datetime(modified_on, '+8 hours')
where modified_on not null;
