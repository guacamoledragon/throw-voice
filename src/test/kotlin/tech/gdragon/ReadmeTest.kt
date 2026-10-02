package tech.gdragon

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

class ReadmeTest : FunSpec({
  test("README.md is src/site/README.md with each link to a site page written as https://pawa.im/#/<page>") {
    val home = File("src/site/README.md").readText()
      .replace(Regex("""\]\(([a-z-]+)\.md\)"""), "](https://pawa.im/#/\$1)")

    File("README.md").readText() shouldBe home
  }
})
