alter table recordings
alter column modified_on type timestamptz using modified_on::timestamptz;
