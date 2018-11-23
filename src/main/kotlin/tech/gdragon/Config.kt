package tech.gdragon

import com.natpryce.konfig.PropertyGroup
import com.natpryce.konfig.getValue
import com.natpryce.konfig.stringType

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
