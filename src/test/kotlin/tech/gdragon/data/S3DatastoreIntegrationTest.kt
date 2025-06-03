package tech.gdragon.data

import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.smithy.kotlin.runtime.content.decodeToString
import io.kotest.core.annotation.EnabledCondition
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Tags("integration")
@EnabledIf(S3DatastoreEnabled::class)
class S3DatastoreIntegrationTest : FunSpec({

  val accessKey = System.getenv("DS_ACCESS_KEY")
  val secretKey = System.getenv("DS_SECRET_KEY")
  val endpoint = System.getenv("DS_HOST")
  val region = System.getenv("DS_REGION")
  val baseUrl = System.getenv("DS_BASEURL")
  val bucketName = System.getenv("DS_BUCKET")

  lateinit var s3Datastore: S3Datastore

  beforeSpec {
    s3Datastore = S3Datastore(
      bucketName = bucketName,
      baseUrl = baseUrl,
      accessKey = accessKey,
      secretKey = secretKey,
      region = region,
      endpoint = endpoint
    )
  }

  afterSpec {
    s3Datastore.shutdown()
  }

  test("upload and download") {
    // Given
    val key = "${Uuid.random()}.mp3"
    val testFile = File.createTempFile("b2-test-audio", ".mp3")
    val testContent = "This is a real integration test with S3 Datastore"
    testFile.writeText(testContent)
    testFile.deleteOnExit()

    // When - Upload
    val uploadResult = s3Datastore.upload(key, testFile)

    // Then
    uploadResult.key shouldBe testFile.name

    // When - Download
    val downloadedContent = runBlocking {
      val request = GetObjectRequest {
        bucket = bucketName
        this.key = key
      }
      s3Datastore.client.getObject(request) {
        it.body?.decodeToString()
      }
    }

    // Then
    downloadedContent shouldNotBe null
    downloadedContent shouldBe testContent
  }
})

class S3DatastoreEnabled : EnabledCondition {
  override fun enabled(kclass: KClass<out Spec>): Boolean {
    val required = listOf("DS_ACCESS_KEY", "DS_SECRET_KEY", "DS_HOST", "DS_REGION", "DS_BASEURL", "DS_BUCKET")
    return required.all { System.getenv(it)?.isNotBlank() == true }
  }
}
