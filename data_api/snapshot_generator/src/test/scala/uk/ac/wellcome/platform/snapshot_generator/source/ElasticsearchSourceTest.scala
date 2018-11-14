package uk.ac.wellcome.platform.snapshot_generator.source

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.IdentifiedWork
import uk.ac.wellcome.test.fixtures.{Akka, TestWith}

class ElasticsearchSourceTest
    extends FunSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with Akka
    with ElasticsearchFixtures
    with WorksGenerators {

  it("outputs the entire content of the index") {
    withActorSystem { actorSystem =>
      withMaterializer(actorSystem) { actorMaterialiser =>
        withLocalElasticsearchIndex { indexName =>
          implicit val materialiser = actorMaterialiser
          val works = createIdentifiedWorks(count = 10)
          insertIntoElasticsearch(indexName, works: _*)

          withSource(actorSystem, indexName) { source =>
            val future = source.runWith(Sink.seq)

            whenReady(future) { result =>
              result should contain theSameElementsAs works
            }
          }
        }
      }
    }
  }

  it("filters non visible works") {
    withActorSystem { actorSystem =>
      withMaterializer(actorSystem) { actorMaterialiser =>
        withLocalElasticsearchIndex { indexName =>
          implicit val materialiser = actorMaterialiser
          val visibleWorks = createIdentifiedWorks(count = 10)
          val invisibleWorks = createIdentifiedInvisibleWorks(count = 3)

          val works = visibleWorks ++ invisibleWorks
          insertIntoElasticsearch(indexName, works: _*)

          withSource(actorSystem, indexName) { source =>
            val future = source.runWith(Sink.seq)

            whenReady(future) { result =>
              result should contain theSameElementsAs visibleWorks
            }
          }
        }
      }
    }
  }

  private def withSource[R](actorSystem: ActorSystem, indexName: String)(
    testWith: TestWith[Source[IdentifiedWork, NotUsed], R]): R = {
    val source = ElasticsearchWorksSource(
      elasticClient = elasticClient,
      indexName = indexName,
      documentType = documentType)(actorSystem)
    testWith(source)
  }

}
