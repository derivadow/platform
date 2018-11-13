package uk.ac.wellcome.platform.idminter

import com.typesafe.config.{Config, ConfigFactory}
import grizzled.slf4j.Logging
import io.circe.Json
import uk.ac.wellcome.config.messaging.builders.MessagingBuilder
import uk.ac.wellcome.platform.idminter.config.builders.{IdentifiersTableBuilder, RDSBuilder}
import uk.ac.wellcome.platform.idminter.database.{IdentifiersDao, TableProvisioner}
import uk.ac.wellcome.platform.idminter.models.IdentifiersTable
import uk.ac.wellcome.platform.idminter.services.IdMinterWorkerService
import uk.ac.wellcome.platform.idminter.steps.{IdEmbedder, IdentifierGenerator}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends App with Logging {
  val config: Config = ConfigFactory.load()

  val identifiersTableConfig = IdentifiersTableBuilder.buildConfig(config)

  val identifierGenerator = new IdentifierGenerator(
    identifiersDao = new IdentifiersDao(
      db = RDSBuilder.buildDB(config),
      identifiers = new IdentifiersTable(
        identifiersTableConfig = identifiersTableConfig
      )
    )
  )

  val tableProvisioner = new TableProvisioner(
    rdsClientConfig = RDSBuilder.buildRDSClientConfig(config)
  )

  tableProvisioner.provision(
    database = identifiersTableConfig.database,
    tableName = identifiersTableConfig.tableName
  )

  val idEmbedder = new IdEmbedder(
    identifierGenerator = identifierGenerator
  )

  val workerService = new IdMinterWorkerService(
    idEmbedder = idEmbedder,
    writer = MessagingBuilder.buildMessageWriter[Json](config),
    messageStream = MessagingBuilder.buildMessageStream[Json](config)
  )

  try {
    info("Starting worker.")

    val result = workerService.run()

    Await.result(result, Duration.Inf)
  } catch {
    case e: Throwable =>
      error("Fatal error:", e)
  } finally {
    info("Terminating worker.")
  }
}
