package tech.gdragon.data

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headObject
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File

class S3DatastoreTest : FunSpec({
  lateinit var seaweedContainer: GenericContainer<*>
  lateinit var s3Datastore: S3Datastore

  val testBucketName = "test-recordings"
  val testAccessKey = "seaweedadmin"
  val testSecretKey = "seaweedadmin"
  lateinit var endpoint: String

  beforeSpec {
    // SeaweedFS S3 gateway; the AWS_* variables create its admin identity
    seaweedContainer = GenericContainer("chrislusf/seaweedfs:4.47")
      .withCommand("server", "-s3")
      .withEnv("AWS_ACCESS_KEY_ID", testAccessKey)
      .withEnv("AWS_SECRET_ACCESS_KEY", testSecretKey)
      .withExposedPorts(8333)
      .waitingFor(Wait.forHttp("/healthz").forPort(8333))

    seaweedContainer.start()

    endpoint = "http://${seaweedContainer.host}:${seaweedContainer.firstMappedPort}"

    // Initialize your S3Datastore with test configuration
    s3Datastore = S3Datastore(
      bucketName = testBucketName,
      endpoint = endpoint,
      accessKey = testAccessKey,
      secretKey = testSecretKey,
      region = "us-east-1",
      baseUrl = "http://localhost:9000"
    )
  }

  afterSpec {
    seaweedContainer.stop()
  }

  test("upload file successfully") {
    // Given
    val key = "recordings/test-session-123.mp3"
    val testFile = File.createTempFile("test-audio", ".mp3")
    testFile.writeText("This is test audio content")
    testFile.deleteOnExit()

    // When
    val result = s3Datastore.upload(key, testFile)

    // Then
    result.key shouldBe testFile.name

    // Verify file exists
    val s3Client = S3Client {
      region = "us-east-1"
      endpointUrl = aws.smithy.kotlin.runtime.net.url.Url.parse(endpoint)
      credentialsProvider = StaticCredentialsProvider {
        accessKeyId = testAccessKey
        secretAccessKey = testSecretKey
      }
      forcePathStyle = true
    }
    runBlocking {
      val response = s3Client.headObject {
        bucket = testBucketName
        this.key = key
      }

      response shouldNotBe null
      response.contentLength shouldBe testFile.length()

      s3Client.close()
    }
  }
})
