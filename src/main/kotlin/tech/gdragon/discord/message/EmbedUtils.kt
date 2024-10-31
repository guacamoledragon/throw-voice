package tech.gdragon.discord.message

import java.time.Instant

/**
 * Convert [Instant] to formatted date time e.g. April 20, 2021 2:20 PM
 *
 * See: https://discord.com/developers/docs/reference#message-formatting-timestamp-styles
 */
fun formatShortDateTime(instant: Instant): String = "<t:${instant.epochSecond}:f>"

/**
 * Convert [Instant] to relative time e.g. 2 months ago
 *
 * See: https://discord.com/developers/docs/reference#message-formatting-timestamp-styles
 */
fun formatRelativeTime(instant: Instant): String = "<t:${instant.epochSecond}:R>"
