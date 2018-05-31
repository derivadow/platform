package uk.ac.wellcome.platform.matcher.storage

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.{
  BatchGetItemRequest,
  PutItemRequest,
  QueryRequest
}
import com.gu.scanamo.Scanamo
import com.gu.scanamo.syntax._
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.platform.matcher.models.WorkNode
import uk.ac.wellcome.storage.dynamo.DynamoConfig

class WorkNodeDaoTest
    extends FunSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with MatcherFixtures {

  describe("Get from dynamo") {
    it("returns nothing if ids are not in dynamo") {
      withLocalDynamoDbTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          whenReady(workNodeDao.get(Set("Not-there"))) { workNodeSet =>
            workNodeSet shouldBe Set.empty
          }
        }
      }
    }

    it("returns WorkNodes which are stored in DynamoDB") {
      withLocalDynamoDbTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          val workNodeA = WorkNode("A", List("B"), "A+B")
          val workNodeB = WorkNode("B", Nil, "A+B")
          Scanamo.put(dynamoDbClient)(table.name)(workNodeA)
          Scanamo.put(dynamoDbClient)(table.name)(workNodeB)

          whenReady(workNodeDao.get(Set("A", "B"))) { workNodeSet =>
            workNodeSet shouldBe Set(workNodeA, workNodeB)
          }
        }
      }
    }

    it("returns an error if fetching from dynamo fails") {
      withLocalDynamoDbTable { table =>
        val dynamoDbClient = mock[AmazonDynamoDB]
        val expectedException = new RuntimeException("FAILED!")
        when(dynamoDbClient.batchGetItem(any[BatchGetItemRequest]))
          .thenThrow(expectedException)
        val matcherGraphDao = new WorkNodeDao(
          dynamoDbClient,
          DynamoConfig(table.name, Some(table.index)))

        whenReady(matcherGraphDao.get(Set("A")).failed) { failedException =>
          failedException shouldBe expectedException
        }
      }
    }

    it("returns an error if Scanamo fails") {
      withLocalDynamoDbTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          case class BadRecord(id: String)
          val badRecord: BadRecord = BadRecord(id = "A")
          Scanamo.put(dynamoDbClient)(table.name)(badRecord)

          whenReady(workNodeDao.get(Set("A")).failed) { failedException =>
            failedException shouldBe a[RuntimeException]
          }
        }
      }
    }
  }

  describe("Get by SetIds") {
    it("returns empty set if componentIds are not in dynamo") {
      withLocalDynamoDbTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          whenReady(workNodeDao.getByComponentIds(Set("Not-there"))) {
            workNodeSet =>
              workNodeSet shouldBe Set()
          }
        }
      }
    }

    it("returns WorkNodes which are stored in DynamoDB") {
      withLocalDynamoDbTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          val workNodeA = WorkNode("A", List("B"), "A+B")
          val workNodeB = WorkNode("B", Nil, "A+B")
          Scanamo.put(dynamoDbClient)(table.name)(workNodeA)
          Scanamo.put(dynamoDbClient)(table.name)(workNodeB)

          whenReady(workNodeDao.getByComponentIds(Set("A+B"))) { workNodeSet =>
            workNodeSet shouldBe Set(workNodeA, workNodeB)
          }
        }
      }
    }

    it("returns an error if fetching from dynamo fails") {
      withLocalDynamoDbTable { table =>
        val dynamoDbClient = mock[AmazonDynamoDB]
        val expectedException = new RuntimeException("FAILED")
        when(dynamoDbClient.query(any[QueryRequest]))
          .thenThrow(expectedException)
        val workNodeDao = new WorkNodeDao(
          dynamoDbClient,
          DynamoConfig(table.name, Some(table.index)))

        whenReady(workNodeDao.getByComponentIds(Set("A+B")).failed) {
          failedException =>
            failedException shouldBe expectedException
        }
      }
    }

    it("returns an error if Scanamo fails") {
      withLocalDynamoDbTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          case class BadRecord(id: String, componentId: String)
          val badRecord: BadRecord = BadRecord(id = "A", componentId = "A+B")
          Scanamo.put(dynamoDbClient)(table.name)(badRecord)

          whenReady(workNodeDao.getByComponentIds(Set("A+B")).failed) {
            failedException =>
              failedException shouldBe a[RuntimeException]
          }
        }
      }
    }
  }

  describe("Insert into dynamo") {
    it("puts a WorkNode") {
      withLocalDynamoDbTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          val workNode = WorkNode("A", List("B"), "A+B")
          whenReady(workNodeDao.put(workNode)) { _ =>
            val savedWorkNode =
              Scanamo.get[WorkNode](dynamoDbClient)(table.name)('id -> "A")
            savedWorkNode shouldBe Some(Right(workNode))
          }
        }
      }
    }

    it("returns an error if putting to dynamo fails") {
      withLocalDynamoDbTable { table =>
        val dynamoDbClient = mock[AmazonDynamoDB]
        val expectedException = new RuntimeException("FAILED")
        when(dynamoDbClient.putItem(any[PutItemRequest]))
          .thenThrow(expectedException)
        val workNodeDao = new WorkNodeDao(
          dynamoDbClient,
          DynamoConfig(table.name, Some(table.index)))

        whenReady(workNodeDao.put(WorkNode("A", List("B"), "A+B")).failed) {
          failedException =>
            failedException shouldBe expectedException
        }
      }
    }
  }

  it("cannot be instantiated if dynamoConfig.index is a None") {
    intercept[RuntimeException] {
      new WorkNodeDao(dynamoDbClient, DynamoConfig("something", None))
    }
  }

}
