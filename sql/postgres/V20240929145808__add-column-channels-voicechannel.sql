alter table public.channels
  add voicechannel bigint default null;

comment on column public.channels.voicechannel is 'If specified, recordings from that voice channel, will be sent to this text channel.';
