package uk.ac.wellcome.platform.archive.common.config.builders

import java.net.URL

import com.typesafe.config.Config
import uk.ac.wellcome.config.core.builders.EnrichConfig._
import uk.ac.wellcome.config.core.models.HTTPServerConfig

object HTTPServerBuilder {
  def buildHTTPServerConfig(config: Config): HTTPServerConfig = {
    val host = config.required[String]("http.host")
    val port = config.required[Int]("http.port")
    val externalBaseURL = config.required[String]("http.externalBaseURL")

    HTTPServerConfig(
      host = host,
      port = port,
      externalBaseURL = externalBaseURL
    )
  }

  def buildContextURL(config: Config): URL =
    new URL(config.required[String]("contextURL"))
}
