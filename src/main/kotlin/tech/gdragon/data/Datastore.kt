package tech.gdragon.data

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headBucket
import aws.sdk.kotlin.services.s3.model.CreateBucketRequest
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.fromFile
import aws.smithy.kotlin.runtime.net.url.Url
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import net.jodah.failsafe.Failsafe
import net.jodah.failsafe.RetryPolicy
import org.apache.commons.io.FileUtils
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
  fun shutdown()
}

class LocalDatastore(private val localBucket: String) : Datastore {
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

  override fun shutdown() {}
}

class S3Datastore(
  private val accessKey: String,
  private val secretKey: String,
  endpoint: String,
  region: String,
  val bucketName: String,
  private val baseUrl: String
) : Datastore {
  val logger = KotlinLogging.logger { }

  val client = S3Client {
    endpointUrl = Url.parse(endpoint)
    this.region = region
    credentialsProvider = StaticCredentialsProvider {
      accessKeyId = accessKey
      secretAccessKey = secretKey
    }
  }

  private val retryPolicy: RetryPolicy<Unit> = RetryPolicy<Unit>()
    .withBackoff(2, 30, ChronoUnit.SECONDS)
    .withJitter(.25)
    .onRetry { ex -> logger.warn { "Failure #${ex.attemptCount}. Retrying!" } }
    .onFailure { ex -> logger.error(ex.failure) { "Failed to upload file!" } }

  init {
    runBlocking {
      client.ensureBucketExists(bucketName)
    }
  }

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

  override fun shutdown() {
    client.close()
  }

  /** Check for valid S3 configuration based on account
   *  Source: https://github.com/awslabs/aws-sdk-kotlin/blob/f8c91219b8ba6cea738d964d711495fa89d5b4be/examples/s3-media-ingestion/src/main/kotlin/aws/sdk/kotlin/example/Main.kt
   */
  private suspend fun S3Client.ensureBucketExists(bucketName: String) {
    if (!bucketExists(bucketName)) {
      val createBucketRequest = CreateBucketRequest {
        bucket = bucketName
      }
      createBucket(createBucketRequest)
    } else {
      logger.info { "Bucket already exists, carry on!" }
    }
  }

  /** Determine if a object exists in a bucket
   *  Source: https://github.com/awslabs/aws-sdk-kotlin/blob/f8c91219b8ba6cea738d964d711495fa89d5b4be/examples/s3-media-ingestion/src/main/kotlin/aws/sdk/kotlin/example/Main.kt
   */
  private suspend fun S3Client.bucketExists(s3bucket: String) =
    try {
      headBucket { bucket = s3bucket }
      true
    } catch (e: Exception) { // Checking Service Exception coming in future release
      false
    }
}

data class UploadResult(val key: String, val timestamp: Instant, val size: Long, val url: String) {
  companion object {
    fun from(path: Path, url: String = path.toUri().toString()): UploadResult {
      val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
      val createdTime = attributes.creationTime().toInstant()
      return UploadResult(path.fileName.toString(), createdTime, attributes.size(), url)
    }
  }
}
