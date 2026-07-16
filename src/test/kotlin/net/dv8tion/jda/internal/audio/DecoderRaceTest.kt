package net.dv8tion.jda.internal.audio

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile

/**
 * Proves the JDA Decoder close/decode race that SIGSEGVs production (~1 crash/day,
 * see .agent/plans/production-stability-debugging.md) and that our patched Decoder
 * (src/main/kotlin/net/dv8tion/jda/internal/audio/Decoder.java) fixes it.
 *
 * The repro runs in a forked JVM because the stock failure mode is a native JVM crash.
 */
class DecoderRaceTest : FunSpec({

  // Surefire may run tests with a manifest-only booter jar; expand it so the
  // forked JVM gets the real classpath.
  fun effectiveClasspath(): List<String> {
    val entries = System.getProperty("java.class.path").split(File.pathSeparator)
    val booter = entries.singleOrNull()?.takeIf { it.endsWith(".jar") } ?: return entries
    val manifestClasspath = JarFile(booter).use { it.manifest?.mainAttributes?.getValue("Class-Path") } ?: return entries
    return manifestClasspath.split(" ").map { File(java.net.URI(it)).absolutePath }
  }

  fun runRepro(classpath: List<String>): Pair<Int, String> {
    val java = File(System.getProperty("java.home"), "bin/java").absolutePath
    val process = ProcessBuilder(
      java,
      "-XX:ErrorFile=/dev/null", // the crash is expected; don't litter hs_err files
      "-cp", classpath.joinToString(File.pathSeparator),
      "net.dv8tion.jda.internal.audio.DecoderCloseRepro",
    )
      .directory(File("target"))
      .redirectErrorStream(true)
      .start()

    val output = process.inputStream.readBytes().decodeToString()
    process.waitFor(60, TimeUnit.SECONDS) shouldBe true
    return process.exitValue() to output
  }

  val classpath = effectiveClasspath()

  test("canary: stock JDA Decoder crashes the JVM on decode-after-close (when this fails, upstream fixed the bug and our patched Decoder can be deleted)") {
    val jdaJar = classpath.first { File(it).name.matches(Regex("JDA-.*\\.jar")) }
    // JDA jar first so its stock Decoder shadows our patched copy in target/classes.
    val (exitCode, output) = runRepro(listOf(jdaJar) + classpath)

    output shouldNotContain "SANITY_FAIL"
    output shouldNotContain "OK afterClose"
    exitCode shouldNotBe 0
  }

  test("patched Decoder drops the packet (returns null) on decode-after-close instead of crashing") {
    val (exitCode, output) = runRepro(classpath)

    output shouldContain "OK afterClose=null"
    exitCode shouldBe 0
  }
})
