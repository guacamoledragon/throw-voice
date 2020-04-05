package tech.gdragon.metrics

import com.rollbar.api.payload.Payload
import com.rollbar.notifier.config.Config
import com.rollbar.notifier.config.ConfigBuilder.withAccessToken
import com.rollbar.notifier.provider.server.ServerProvider
import com.rollbar.notifier.sender.SyncSender
import com.rollbar.notifier.sender.json.JsonSerializer
import com.rollbar.notifier.sender.result.Result
import org.koin.core.KoinComponent

class Rollbar : KoinComponent {
  private val environment: String? = getKoin().getProperty("ROLLBAR_ENV") ?: "test"
  private val token: String? = getKoin().getProperty("ROLLBAR_TOKEN") ?: "83d0645ad4d64d3ab6715f2c07a47b24"

  object Serializer : JsonSerializer {
    override fun resultFrom(response: String?): Result {
      val pattern = Regex("\"deploy_id\"\\s*:\\s*(\\d*)")
      val match = pattern.find(response ?: "")
      val code = if (match == null) -1 else 0

      return Result.Builder()
        .code(code)
        .body(match?.groupValues?.get(1))
        .build()
    }

    override fun toJson(payload: Payload?): String = payload?.json ?: ""
  }

  val config: Config = withAccessToken(token)
    .environment(environment)
    .server(ServerProvider())
    .sender(SyncSender.Builder("https://api.rollbar.com/api/1/deploy/").accessToken(token).jsonSerializer(Serializer).build())
    .language("kotlin")
    .build()

  val rollbar = com.rollbar.notifier.Rollbar(config)

  fun deploy(): Unit {
    val revision = getKoin().getProperty("VERSION", "dev")
    rollbar.sendJsonPayload("""
        {
          "access_token": "$token",
          "environment": "$environment",
          "revision": "$revision",
          "status": "completed"
        }
      """.trimIndent())
  }
}
