package tech.gdragon.data

import org.koin.core.context.startKoin
import org.koin.dsl.module
import tech.gdragon.koin.overrideFileProperties

fun main() {
  val app = startKoin {
    overrideFileProperties("dev-b2.properties")
    modules(
      module {
      single<Datastore>(createdAtStart = true) {
        val bucketName = getProperty<String>("DS_BUCKET")
        val accessKey = getProperty<String>("DS_ACCESS_KEY")
        val secretKey = getProperty<String>("DS_SECRET_KEY")
        val endpoint = getProperty<String>("DS_HOST")
        val region = getProperty<String>("DS_REGION")
        val baseUrl = getProperty<String>("DS_BASEURL")
        S3Datastore(
          accessKey,
          secretKey,
          endpoint,
          region,
          bucketName,
          baseUrl
        )
      }
    })
  }

  val datastore = app.koin.get<Datastore>() as S3Datastore
  datastore.client.close()
  app.close()
}
