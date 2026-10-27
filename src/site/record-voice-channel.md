# How to Record a Discord Voice Channel

Invite pawa, join the voice channel, and type `/record`. When you're done, type `/save`. You get one MP3 of the whole conversation.

## Steps

1. **Invite pawa to your server.** You need the Manage Server permission to add a bot.

   <a class="button" href="https://discord.com/oauth2/authorize?client_id=338897906524225538&scope=applications.commands+bot&permissions=2251344896">Invite!</a>

2. **Join a voice channel and type `/record`.** pawa joins you and records everyone in the channel.
3. **Type `/save` when you're done.** pawa leaves the channel and posts the recording in the text channel where you typed the command. To send it somewhere else, pick a channel: `/save channel:#recordings`.

> [!CAUTION] `/stop` ends the recording **without saving it**. If you type `/stop` instead of `/save`, the audio is gone.

## What you get

One MP3 with everyone mixed together, at 128 kbps. pawa posts a message with the session ID, who spoke, how long it ran, and a download link. When the file fits under Discord's upload limit, it's attached to that message too.

## FAQ

### Is pawa free?

Yes. Recording and saving cost nothing. There's one paid plan, Pawa Starter, at USD 5 a month. The only thing it changes is how long the download link works: 30 days instead of 24 hours. The [terms](terms.md) have the details.

### How long can I record?

About four and a half hours per session. The limit is 256 MB of audio, not a set time. pawa warns you at 90%, so save then and start again. If you keep going, it drops the oldest audio to make room. pawa also leaves by itself after 2 minutes with nobody talking.

### Where does the file go?

To the text channel where you typed `/save`, or the one you picked. The download link works for 24 hours. If Discord took the attachment, that copy stays in the channel as long as the message does.

### Can I record on a phone?

Yes. Slash commands work in the Discord app on iOS and Android, so you can start and save a recording from your phone. The recording happens on pawa's servers, not on your phone.

### Does pawa give each person their own track?

No, you get one mixed file. If you want a track for each speaker so you can edit them apart, Craig does that. See [Craig vs pawa](craig-vs-pawa.md).

<script type="application/ld+json">
{
  "@context": "https://schema.org",
  "@type": "FAQPage",
  "mainEntity": [
    {
      "@type": "Question",
      "name": "Is pawa free?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "Yes. Recording and saving cost nothing. There's one paid plan, Pawa Starter, at USD 5 a month. The only thing it changes is how long the download link works: 30 days instead of 24 hours."
      }
    },
    {
      "@type": "Question",
      "name": "How long can I record?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "About four and a half hours per session. The limit is 256 MB of audio, not a set time. pawa warns you at 90%, so save then and start again. If you keep going, it drops the oldest audio to make room. pawa also leaves by itself after 2 minutes with nobody talking."
      }
    },
    {
      "@type": "Question",
      "name": "Where does the file go?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "To the text channel where you typed /save, or the one you picked. The download link works for 24 hours. If Discord took the attachment, that copy stays in the channel as long as the message does."
      }
    },
    {
      "@type": "Question",
      "name": "Can I record on a phone?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "Yes. Slash commands work in the Discord app on iOS and Android, so you can start and save a recording from your phone. The recording happens on pawa's servers, not on your phone."
      }
    },
    {
      "@type": "Question",
      "name": "Does pawa give each person their own track?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "No, you get one mixed file. If you want a track for each speaker so you can edit them apart, Craig does that."
      }
    }
  ]
}
</script>
