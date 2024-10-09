alter table CHANNELS
  add VOICECHANNEL BIGINT default null;

comment on column CHANNELS.VOICECHANNEL is 'If specified, recordings from that voice channel, will be sent to this text channel.';
