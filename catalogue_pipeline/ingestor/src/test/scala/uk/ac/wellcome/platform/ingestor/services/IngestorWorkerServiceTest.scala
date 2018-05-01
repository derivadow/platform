package uk.ac.wellcome.platform.ingestor.services

import java.net.ConnectException

import akka.actor.ActorSystem
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.sksamuel.elastic4s.http.HttpClient
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Assertion, FunSpec, Matchers}
import uk.ac.wellcome.elasticsearch.finatra.modules.ElasticCredentials
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.exceptions.GracefulFailureException
import uk.ac.wellcome.messaging.metrics.MetricsSender
import uk.ac.wellcome.messaging.sqs.{SQSConfig, SQSMessage, SQSReader}
import uk.ac.wellcome.messaging.test.fixtures.SQS
import uk.ac.wellcome.models.work.internal.{
  IdentifiedWork,
  IdentifierSchemes,
  SourceIdentifier
}
import uk.ac.wellcome.models.aws.SQSConfig
import uk.ac.wellcome.models.work.internal.{IdentifiedWork, IdentifierSchemes, SourceIdentifier}
import uk.ac.wellcome.s3.KeyPrefixGenerator
import uk.ac.wellcome.sqs.SQSReader
import uk.ac.wellcome.test.fixtures.{Messaging, S3, SQS}
import uk.ac.wellcome.test.utils.JsonTestUtil

import scala.concurrent.duration._

class IngestorWorkerServiceTest
    extends FunSpec
    with ScalaFutures
    with Matchers
    with MockitoSugar
    with JsonTestUtil
    with ElasticsearchFixtures
    with SQS
    with S3
    with Messaging {

  val itemType = "work"

  val metricsSender: MetricsSender =
    new MetricsSender(
      namespace = "reindexer-tests",
      flushInterval = 100 milliseconds,
      amazonCloudWatch = mock[AmazonCloudWatch],
      actorSystem = ActorSystem())

  val actorSystem = ActorSystem()

  val identifiedWorkKeyPrefixGenerator = new KeyPrefixGenerator[IdentifiedWork] {
    override def generate(obj: IdentifiedWork): String = ""
  }

  def createMiroWork(
    canonicalId: String,
    sourceId: String,
    title: String,
    visible: Boolean = true,
    version: Int = 1
  ): IdentifiedWork =
    createWork(
      canonicalId,
      sourceId,
      title,
      IdentifierSchemes.miroImageNumber,
      visible,
      version)

  def createSierraWork(
    canonicalId: String,
    sourceId: String,
    title: String,
    visible: Boolean = true,
    version: Int = 1
  ): IdentifiedWork =
    createWork(
      canonicalId,
      sourceId,
      title,
      IdentifierSchemes.sierraSystemNumber,
      visible,
      version)

  def createWork(canonicalId: String,
                 sourceId: String,
                 title: String,
                 identifierScheme: IdentifierSchemes.IdentifierScheme,
                 visible: Boolean = true,
                 version: Int = 1): IdentifiedWork = {
    val sourceIdentifier = SourceIdentifier(
      identifierScheme = identifierScheme,
      ontologyType = "Work",
      value = sourceId
    )

    IdentifiedWork(
      title = Some(title),
      sourceIdentifier = sourceIdentifier,
      version = version,
      identifiers = List(sourceIdentifier),
      canonicalId = canonicalId,
      visible = visible)
  }

  it("inserts an Miro identified Work into v1 and v2 indices") {
    val work: IdentifiedWork = createMiroWork(
      canonicalId = "m7b2aqtw",
      sourceId = "M000765",
      title = "A monstrous monolith of moss"
    )

    val workIndexer =
      new WorkIndexer(itemType, elasticClient, metricsSender)

    withLocalSqsQueue { queue =>
      withLocalS3Bucket { bucket =>
        withMessageReader[IdentifiedWork, Assertion](bucket, identifiedWorkKeyPrefixGenerator) { messageReader =>
          withLocalElasticsearchIndex(itemType = itemType) { indexNameV1 =>
            withLocalElasticsearchIndex(itemType = itemType) { indexNameV2 =>
              val service = new IngestorWorkerService(
                indexNameV1,
                indexNameV2,
                identifiedWorkIndexer = workIndexer,
                sqsReader = new SQSReader(sqsClient, SQSConfig(queue.url, 1.second, 1)),
                messageReader = messageReader,
                system = actorSystem,
                metrics = metricsSender
              )

              service.processMessage(work)

              assertElasticsearchEventuallyHasWork(
                work,
                indexName = indexNameV1,
                itemType = itemType)

              assertElasticsearchEventuallyHasWork(
                work,
                indexName = indexNameV2,
                itemType = itemType)
            }
          }
        }
      }
    }
  }

  it("inserts an Sierra identified Work only into the v2 index") {
    val work = createSierraWork(
      canonicalId = "m7b2aqtw",
      sourceId = "M000765",
      title = "A monstrous monolith of moss"
    )

    val workIndexer =
      new WorkIndexer(itemType, elasticClient, metricsSender)

    withLocalSqsQueue { queue =>
      withLocalS3Bucket { bucket =>
        withMessageReader[IdentifiedWork, Assertion](bucket, identifiedWorkKeyPrefixGenerator) { messageReader =>
          withLocalElasticsearchIndex(itemType = itemType) { indexNameV1 =>
            withLocalElasticsearchIndex(itemType = itemType) { indexNameV2 =>
              val service = new IngestorWorkerService(
                indexNameV1,
                indexNameV2,
                identifiedWorkIndexer = workIndexer,
                sqsReader = new SQSReader(sqsClient, SQSConfig(queue.url, 1.second, 1)),
                messageReader = messageReader,
                system = actorSystem,
                metrics = metricsSender
              )

              service.processMessage(work)

              assertElasticsearchNeverHasWork(
                work,
                indexName = indexNameV1,
                itemType = itemType)

              assertElasticsearchEventuallyHasWork(
                work,
                indexName = indexNameV2,
                itemType = itemType)
            }
          }
        }
      }
    }
  }

  it("fails inserting a non sierra or miro identified work") {
    val work = createWork(
      canonicalId = "m7b2aqtw",
      sourceId = "M000765",
      title = "A monstrous monolith of moss",
      identifierScheme = IdentifierSchemes.calmAltRefNo
    )

    val workIndexer =
      new WorkIndexer(itemType, elasticClient, metricsSender)

    withLocalSqsQueue { queue =>
      withLocalS3Bucket { bucket =>
        withMessageReader[IdentifiedWork, Assertion](bucket, identifiedWorkKeyPrefixGenerator) { messageReader =>
          withLocalElasticsearchIndex(itemType = itemType) { indexNameV1 =>
            withLocalElasticsearchIndex(itemType = itemType) { indexNameV2 =>
              val service = new IngestorWorkerService(
              indexNameV1,
              indexNameV2,
              identifiedWorkIndexer = workIndexer,
              sqsReader = new SQSReader(sqsClient, SQSConfig(queue.url, 1.second, 1)),
              messageReader = messageReader,
              system = actorSystem,
              metrics = metricsSender
            )

              val future = service.processMessage(work)

              whenReady(future.failed) { ex =>
                ex shouldBe a[GracefulFailureException]
                ex.getMessage shouldBe s"Cannot ingest work with identifierScheme: ${IdentifierSchemes.calmAltRefNo}"
              }
            }
          }
        }
      }
    }
  }

  it("returns a failed Future if indexing into Elasticsearch fails") {
    val brokenRestClient: RestClient = RestClient
      .builder(new HttpHost("localhost", 9800, "http"))
      .setHttpClientConfigCallback(
        new ElasticCredentials("elastic", "changeme"))
      .build()

    val brokenElasticClient: HttpClient =
      HttpClient.fromRestClient(brokenRestClient)

    val brokenWorkIndexer = new WorkIndexer(
      esType = "work",
      elasticClient = brokenElasticClient,
      metricsSender = metricsSender
    )

    val work = createMiroWork(
      canonicalId = "b4aurznb",
      sourceId = "B000765",
      title = "A broken beach of basilisks"
    )

    withLocalSqsQueue { queue =>
      withLocalS3Bucket { bucket =>
        withMessageReader[IdentifiedWork, Assertion](bucket, identifiedWorkKeyPrefixGenerator) { messageReader =>

          val service = new IngestorWorkerService(
            "works-v1",
            "works-v2",
            identifiedWorkIndexer = brokenWorkIndexer,
            sqsReader = new SQSReader(sqsClient, SQSConfig(queue.url, 1.second, 1)),
            messageReader = messageReader,
            system = actorSystem,
            metrics = metricsSender
          )

          val future = service.processMessage(work)
          whenReady(future.failed) { result =>
            result shouldBe a[ConnectException]
          }
        }
      }
    }
  }
}
