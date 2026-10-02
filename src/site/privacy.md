# Privacy Policy

Last updated 1 October 2026.

This policy covers **pawa**, the Discord voice-channel recording bot, and **Pawa Companion**,
its iOS app. The app opens recordings the bot made and does nothing on its own, so the two are
described together here.

## The bot

**Recordings.** pawa records the voice channel you tell it to record. A recording contains the
audio of everyone speaking in that channel while it runs. When you `/save`, the recording is
either uploaded to Discord — where it lives under
[Discord's privacy policy](https://discord.com/privacy) and stays as long as the message does —
or stored by us and given a download link. A recording we store is deleted after 1 day (24
hours), or after 30 days if the server has a subscription, and its link stops working at the
same time. We do not listen to recordings, and we do not hand them to anyone.

**What we keep.** Your server's and channels' Discord IDs and names, your bot settings, and
metadata about each recording: its ID, size, timestamps and link. That is what makes the bot
work across restarts. Our database holds no message content and nothing about who was in a
channel. It holds no Discord user IDs either, unless you buy a subscription — see The
subscription below.

**Operating the service.** Our servers keep ordinary logs and performance metrics. They are for
keeping the bot running, not for profiling you.

## The app

Pawa Companion collects nothing. There are no accounts, no analytics, no crash reporting, no
advertising, and no third-party SDKs that collect data. It sends nothing about you to us. Its
only network requests download the recording you opened.

**Downloading.** Opening a pawa recording link makes the app fetch that recording using the
signed link you opened. That request goes to whoever is hosting the file — Discord's CDN, or our
download host — and nothing about you is attached to it.

**Saving.** A saved recording is written to storage on your device and copied nowhere else.
Deleting the app deletes the recordings it saved.

**Transcribing.** Transcription runs entirely on your device using Apple's on-device speech
framework. Neither the audio nor the transcript is uploaded — not to us, not to Apple, not to
anyone.

**Sharing.** If you use the share sheet, the recording goes wherever you send it. What happens
to it after that is up to that destination's own policy.

## Our websites

These are separate from the iOS app, which uses none of what follows.

This documentation site counts page views with Plausible and Cloudflare, which set no cookies.
**app.pawa.im**, where recordings are played and download links open, collects more: it uses
Google Analytics and PostHog, which set cookies and record how the pages are used. A content
blocker or your browser's tracking protection will stop them, and the pages still work.

You can sign in with Discord there. It is optional unless you buy a subscription. We keep your
sign-in only for as long as your session lasts, and if you do not buy, we do not store your
Discord account. Feedback you send from a recording page reaches us on Discord with your
username attached.

## The subscription

This applies only if you buy a subscription on **app.pawa.im**.

**What we keep.** Your Discord user ID, the ID of the subscribed server, the status of the
subscription, and the date it is paid through. We also keep a log of the payment events that
RevenueCat sends us, and those events contain your Discord user ID.

We keep this while the subscription is active. After it ends, we keep it for as long as we
need it for our records, for example for taxes or a payment dispute. You can ask us to delete
it — use the contact below.

**Payment.** RevenueCat sells the subscription and Stripe processes the card. Your Discord user
ID is your customer ID at RevenueCat. The email address and the payment details you type into
the payment form go to RevenueCat and Stripe, under
[RevenueCat's privacy policy](https://www.revenuecat.com/privacy) and
[Stripe's privacy policy](https://stripe.com/privacy). We do not see or store your card
details.

**Your server list.** On the billing page, we read the list of your Discord servers from
Discord, to show the servers you can subscribe. We do not store that list.

## Children

Neither pawa nor Pawa Companion is directed at children under 13.

## Changes

Any change to this policy is posted at this address with a new date.

## Contact

Ask in the [pawa Discord server](https://discord.gg/gkvsNw8), or email <jose@gdragon.tech>.
