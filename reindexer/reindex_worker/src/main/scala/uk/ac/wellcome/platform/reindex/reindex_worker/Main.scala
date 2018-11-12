package uk.ac.wellcome.platform.reindex.reindex_worker

import com.typesafe.config.{Config, ConfigFactory}
import grizzled.slf4j.Logging
import uk.ac.wellcome.config.messaging.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.config.storage.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.platform.reindex.reindex_worker.dynamo.{MaxRecordsScanner, ParallelScanner, ScanSpecScanner}
import uk.ac.wellcome.platform.reindex.reindex_worker.services.{HybridRecordSender, RecordReader, ReindexWorkerService}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends App with Logging {
  val config: Config = ConfigFactory.load()

  val scanSpecScanner = new ScanSpecScanner(
    dynamoDBClient = DynamoBuilder.buildDynamoClient(config)
  )

  val recordReader = new RecordReader(
    maxRecordsScanner = new MaxRecordsScanner(
      scanSpecScanner = scanSpecScanner,
      dynamoConfig = DynamoBuilder.buildDynamoConfig(config)
    ),
    parallelScanner = new ParallelScanner(
      scanSpecScanner = scanSpecScanner,
      dynamoConfig = DynamoBuilder.buildDynamoConfig(config)
    )
  )

  val hybridRecordSender = new HybridRecordSender(
    snsWriter = SNSBuilder.buildSNSWriter(config)
  )

  val workerService = new ReindexWorkerService(
    recordReader = recordReader,
    hybridRecordSender = hybridRecordSender,
    sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config)
  )

  try {
    info(s"Starting worker.")

    val result = workerService.run()

    Await.result(result, Duration.Inf)
  } catch {
    case e: Throwable =>
      error("Fatal error:", e)
  } finally {
    info(s"Terminating worker.")
  }
}
