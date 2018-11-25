package tech.gdragon

import com.natpryce.konfig.*

val appDatabase = Key("db-name", stringType)
val appData = Key("data-dir", stringType)
val appPort = Key("port", intType)
val appVersion = Key("version", stringType)
val appWebsite = Key("website", stringType)

object B2 : PropertyGroup() {
  val account_id by stringType
  val app_key by stringType
  val base_url by stringType
  val bucket_id by stringType
  val bucket_name by stringType
}

object Bot : PropertyGroup() {
  val token by stringType
  val version by stringType
}
