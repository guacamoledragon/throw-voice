package tech.gdragon.discord

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.string.shouldContain
import net.dv8tion.jda.api.Permission
import java.io.File

class BotTest : FunSpec({
  test("the bot asks for embed links, and the site invite link asks for Bot.PERMISSIONS") {
    Bot.PERMISSIONS shouldContain Permission.MESSAGE_EMBED_LINKS
    val permissions = "permissions=${Permission.getRaw(Bot.PERMISSIONS)}\""
    File("src/site/quickstart.md").readText() shouldContain permissions
    File("src/site/README.md").readText() shouldContain permissions
  }
})
