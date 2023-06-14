package tech.gdragon.data

import aws.sdk.kotlin.runtime.auth.credentials.EnvironmentCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.fromFile
import aws.smithy.kotlin.runtime.net.Url
import io.minio.*
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import net.jodah.failsafe.Failsafe
import net.jodah.failsafe.RetryPolicy
import org.apache.commons.io.FileUtils
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.temporal.ChronoUnit

interface Datastore {
  fun upload(key: String, file: File): UploadResult
}

class LocalDatastore(val localBucket: String) : Datastore {
  init {
    val localBucketDirectory = Paths.get(localBucket)
    if (!Files.isDirectory(localBucketDirectory)) {
      Files.createDirectory(localBucketDirectory)
    }
  }

  override fun upload(key: String, file: File): UploadResult {
    FileInputStream(file).use {
      val newFile = Paths.get(localBucket, key)
      Files.createDirectories(newFile.parent)
      Files.copy(it, newFile)
      return UploadResult.from(newFile)
    }
  }
}

@Deprecated("Use S3Datastore instead.")
class RemoteDatastore(
  accessKey: String,
  val bucketName: String,
  endpoint: String,
  secretKey: String,
  val baseUrl: String
) : Datastore {
  val logger = KotlinLogging.logger { }

  private val client: MinioClient = MinioClient
    .builder()
    .endpoint(endpoint)
    .credentials(accessKey, secretKey)
    .build()

  private val retryPolicy: RetryPolicy<Unit> = RetryPolicy<Unit>()
    .withBackoff(2, 30, ChronoUnit.SECONDS)
    .withJitter(.25)
    .onRetry { ex -> logger.warn { "Failure #${ex.attemptCount}. Retrying!" } }

  init {
    if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
      logger.warn {
        "$bucketName bucket does not exist! Creating..."
      }
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build())
    }
  }

  override fun upload(key: String, file: File): UploadResult {
    logger.info {
      "Uploading: $baseUrl/$key"
    }

    Failsafe.with(retryPolicy).run { ->
      ByteArrayInputStream(file.readBytes()).use { bais ->
        val putObjectArgs = PutObjectArgs
          .builder()
          .contentType("audio/mpeg")
          .bucket(bucketName)
          .`object`(key)
          .stream(bais, bais.available().toLong(), -1)
          .build()
        client.putObject(putObjectArgs)
      }
    }

    val statObjectArgs = StatObjectArgs
      .builder()
      .bucket(bucketName)
      .`object`(key)
      .build()
    val stat = UploadResult.from(baseUrl, client.statObject(statObjectArgs))

    logger.info {
      "Finished uploading file - (${FileUtils.byteCountToDisplaySize(stat.size)}) ${stat.key}"
    }

    return stat
  }
}

class S3Datastore(
  endpoint: String,
  region: String,
  val bucketName: String,
  val baseUrl: String,
) : Datastore {
  val logger = KotlinLogging.logger { }

  val client = S3Client {
    this.endpointUrl = Url.parse(endpoint)
    this.region = region
    this.credentialsProvider = EnvironmentCredentialsProvider()
  }

  private val retryPolicy: RetryPolicy<Unit> = RetryPolicy<Unit>()
    .withBackoff(2, 30, ChronoUnit.SECONDS)
    .withJitter(.25)
    .onRetry { ex -> logger.warn { "Failure #${ex.attemptCount}. Retrying!" } }

  override fun upload(key: String, file: File): UploadResult {
    Failsafe.with(retryPolicy).run { ->
      runBlocking {
        client.putObject {
          bucket = bucketName
          this.key = key
          body = ByteStream.fromFile(file)
          contentType = "audio/mpeg"
        }
      }
    }

    logger.info {
      "Finished uploading file - (${FileUtils.byteCountToDisplaySize(file.length())}) $key"
    }

    return UploadResult.from(file.toPath(), "$baseUrl/$key")
  }
}

data class UploadResult(val key: String, val timestamp: Instant, val size: Long, val url: String) {
  companion object {
    fun from(baseUrl: String, stat: StatObjectResponse): UploadResult {
      val createdTime = stat.lastModified().toInstant()
      stat.lastModified().toInstant()

      return UploadResult(stat.`object`(), createdTime, stat.size(), "$baseUrl/${stat.`object`()}")
    }

    fun from(path: Path, url: String = path.toUri().toString()): UploadResult {
      val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
      val createdTime = attributes.creationTime().toInstant()
      return UploadResult(path.fileName.toString(), createdTime, attributes.size(), url)
    }
  }
}
