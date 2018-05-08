package uk.ac.wellcome.messaging.message

import akka.actor.ActorSystem
import org.mockito.Matchers.{any, anyDouble, endsWith, eq => equalTo}
import org.mockito.Mockito.{never, times, verify}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSConfig
import uk.ac.wellcome.messaging.test.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.test.fixtures.{Messaging, SQS}
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.monitoring.test.fixtures.MetricsSenderFixture
import uk.ac.wellcome.storage.s3.{S3Config, S3ObjectLocation}
import uk.ac.wellcome.storage.test.fixtures.S3.Bucket
import uk.ac.wellcome.test.fixtures.{Akka, TestWith}
import uk.ac.wellcome.test.utils.ExtendedPatience
import uk.ac.wellcome.utils.JsonUtil._

import scala.concurrent.Future
import scala.concurrent.duration._

class MessageStreamTest
    extends FunSpec
    with Matchers
    with Messaging
    with Akka
    with ScalaFutures
    with ExtendedPatience
    with MetricsSenderFixture {

  it("reads messages off a queue, processes them and deletes them") {

    withMessageStreamFixtures {
      case (bucket, messageStream, QueuePair(queue, dlq)) =>
        val key = "message-key"
        val exampleObject = ExampleObject("some value")

        val notice = put(exampleObject, S3ObjectLocation(bucket.name, key))

        sqsClient.sendMessage(
          queue.url,
          notice
        )

        var received: List[ExampleObject] = Nil
        val f = (o: ExampleObject) => {

          synchronized {
            received = o :: received
          }

          Future.successful(())
        }
        messageStream.foreach("test-stream", f)

        eventually {
          received shouldBe List(exampleObject)

          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }

  }

  it("increments *_ProcessMessage metric when successful") {
    withMessageStreamFixturesMockedMetrics {
      case (bucket, messageStream, QueuePair(queue, dlq), metricsSender) =>
        val key = "message-key"
        val exampleObject = ExampleObject("some value")

        val notice = put(exampleObject, S3ObjectLocation(bucket.name, key))

        sqsClient.sendMessage(
          queue.url,
          notice
        )

        var received: List[ExampleObject] = Nil
        val f = (o: ExampleObject) => {

          synchronized {
            received = o :: received
          }

          Future.successful(())
        }
        messageStream.foreach("test-stream", f)

        eventually {
          verify(metricsSender, times(1)).timeAndCount(equalTo("test-stream_ProcessMessage"), any())
        }
    }
  }

    it("fails gracefully when NotificationMessage cannot be deserialised") {
      withMessageStreamFixturesMockedMetrics {
        case (_, messageStream, QueuePair(queue, dlq), metricsSender) =>
          sqsClient.sendMessage(
            queue.url,
            "not valid json"
          )

          var received: List[ExampleObject] = Nil
          val f = (o: ExampleObject) => {

            synchronized {
              received = o :: received
            }

            Future.successful(())
          }

          messageStream.foreach("test-stream", f)

          eventually {

          verify(metricsSender, never())
            .incrementCount(endsWith("_MessageProcessingFailure"), anyDouble())
          received shouldBe Nil

            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)
          }
      }
    }

    it("does not fail gracefully when the s3 object cannot be retrieved") {
      withMessageStreamFixturesMockedMetrics {
        case (bucket, messageStream, QueuePair(queue, dlq), metricsSender) =>
          val key = "key.json"

          // Do NOT put S3 object here

          val examplePointer =
            MessagePointer(S3ObjectLocation(bucket.name, key))
          val serialisedExamplePointer = toJson(examplePointer).get

          val exampleNotification = NotificationMessage(
            MessageId = "MessageId",
            TopicArn = "TopicArn",
            Subject = "Subject",
            Message = serialisedExamplePointer
          )

          val serialisedExampleNotification = toJson(exampleNotification).get

          sqsClient.sendMessage(
            queue.url,
            serialisedExampleNotification
          )

          var received: List[ExampleObject] = Nil
          val f = (o: ExampleObject) => {

            synchronized {
              received = o :: received
            }

            Future.successful(())
          }

          messageStream.foreach("test-stream", f)

        eventually {
          verify(metricsSender, times(3)).incrementCount(
            metricName = "test-stream_MessageProcessingFailure",
            count = 1.0)

            received shouldBe Nil

            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)
          }
      }
    }

  it("continues reading if processing of some messages fails ") {
      withMessageStreamFixtures {
        case (bucket, messageStream, QueuePair(queue, dlq)) =>
          val key = "message-key"
          val exampleObject = ExampleObject("some value")

          sqsClient.sendMessage(
            queue.url,
            "not valid json"
          )

          val firstNotice =
            put(exampleObject, S3ObjectLocation(bucket.name, key))

          sqsClient.sendMessage(
            queue.url,
            firstNotice
          )

          sqsClient.sendMessage(
            queue.url,
            "another not valid json"
          )

          val secondNotice =
            put(exampleObject, S3ObjectLocation(bucket.name, key))

          sqsClient.sendMessage(
            queue.url,
            secondNotice
          )

          var received: List[ExampleObject] = Nil
          val f = (o: ExampleObject) => {

            synchronized {
              received = o :: received
            }

            Future.successful(())
          }
          messageStream.foreach("test-stream", f)

          eventually {
            received shouldBe List(exampleObject, exampleObject)

            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 2)
          }
      }
    }

    def withMessageStreamFixturesMockedMetrics[R](
                                      testWith: TestWith[(Bucket,
                                        MessageStream[ExampleObject],
                                        QueuePair,
                                        MetricsSender),
                                        R]) = {

      withActorSystem { actorSystem =>
          withLocalS3Bucket { bucket =>
            withLocalSqsQueueAndDlq {
              case queuePair@QueuePair(queue, dlq) =>
                withMockMetricSender { metricsSender =>
                  withMessageStream(actorSystem, bucket, queue, metricsSender) { stream =>
                    testWith((bucket, stream, queuePair, metricsSender))
                  }

            }
          }
        }
      }
    }


    def withMessageStreamFixtures[R](
                                      testWith: TestWith[(Bucket,
                                        MessageStream[ExampleObject],
                                        QueuePair),
                                        R]) = {

      withActorSystem { actorSystem =>
          withLocalS3Bucket { bucket =>
            withLocalSqsQueueAndDlq {
              case queuePair@QueuePair(queue, dlq) =>
                withMetricsSender(actorSystem) { metricsSender =>
                withMessageStream(actorSystem, bucket, queue, metricsSender) { stream =>
                  testWith((bucket, stream, queuePair))
                }
            }
          }
        }
      }
    }

  private def withMessageStream[R](actorSystem: ActorSystem, bucket: Bucket, queue: SQS.Queue, metricsSender: MetricsSender)(testWith: TestWith[MessageStream[ExampleObject], R]) = {
    val s3Config = S3Config(bucketName = bucket.name)
    val sqsConfig = SQSConfig(
      queueUrl = queue.url,
      waitTime = 1 millisecond,
      maxMessages = 1)

    val messageConfig = MessageReaderConfig(
      sqsConfig = sqsConfig,
      s3Config = s3Config
    )

    val stream = new MessageStream[ExampleObject](
      actorSystem,
      asyncSqsClient,
      s3Client,
      messageConfig,
      metricsSender)
    testWith(stream)
  }
}