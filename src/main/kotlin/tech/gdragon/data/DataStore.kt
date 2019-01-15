package tech.gdragon.data

import io.minio.MinioClient
import io.minio.ObjectStat
import mu.KotlinLogging
import org.apache.commons.io.FileUtils
import org.joda.time.DateTime
import java.io.File
import java.io.InputStream

class DataStore(endpoint: String, private val bucketName: String, accessKey: String = "", secretKey: String = "") {
  companion object {
    fun createDataStore(bucketName: String): DataStore = DataStore(
      System.getenv("DS_HOST"),
      bucketName,
      System.getenv("B2_ACCOUNT_ID"),
      System.getenv("B2_APP_KEY")
    )
  }

  val logger = KotlinLogging.logger { }
  private val client: MinioClient = MinioClient(endpoint, accessKey, secretKey)
  val baseUrl: String = (System.getenv("B2_BASE_URL") ?: endpoint)

  init {
    require(client.bucketExists(bucketName)) {
      "$bucketName bucket does not exist!"
    }
  }

  fun upload(key: String, file: File): UploadResult {
    val url = "$baseUrl/${bucketName.replace("-recordings", "")}"

    logger.info {
      "Ready to upload recording to - $url$key"
    }

    client.putObject(bucketName, key, file.path)
    val stat = UploadResult.from(url, client.statObject(bucketName, key))

    logger.info {
      "Finished uploading file - (${FileUtils.byteCountToDisplaySize(stat.size)}) ${stat.key}"
    }

    return stat
  }

  fun upload(key: String, stream: InputStream, contentType: String = "audio/mpeg") {
    client.putObject(bucketName, key, stream, contentType)
  }
}

data class UploadResult(val key: String, val timestamp: DateTime, val size: Long, val url: String) {
  companion object {
    fun from(baseUrl: String, stat: ObjectStat) = UploadResult(stat.name(), DateTime(stat.createdTime()), stat.length(), "$baseUrl${stat.name()}")
  }
}
