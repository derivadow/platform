package uk.ac.wellcome.platform.archive.registrar.http

import java.time.Instant

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Inside, Matchers}
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture
import uk.ac.wellcome.platform.archive.common.fixtures.RandomThings
import uk.ac.wellcome.platform.archive.common.models.DisplayStorageSpace
import uk.ac.wellcome.platform.archive.registrar.common.models._
import uk.ac.wellcome.platform.archive.registrar.http.fixtures.RegistrarHttpFixture
import uk.ac.wellcome.platform.archive.registrar.http.models.{DisplayBag, DisplayFileManifest}
import uk.ac.wellcome.storage.vhs.EmptyMetadata
import uk.ac.wellcome.storage.dynamo._

class RegistrarHttpFeatureTest
    extends FunSpec
    with Matchers
    with ScalaFutures
    with MetricsSenderFixture
    with RegistrarHttpFixture
    with RandomThings
    with IntegrationPatience with Inside {

  import HttpMethods._
  import uk.ac.wellcome.json.JsonUtil._

  describe("GET /registrar/:space/:id") {
    it("returns a bag when available") {
      withConfiguredApp {
        case (vhs, baseUrl, app) =>
          app.run()

          withActorSystem { implicit actorSystem =>
            withMaterializer(actorSystem) { implicit actorMaterializer =>
              val bagId = randomBagId

              val sourceIdentifier = SourceIdentifier(
                IdentifierType("source", "Label"),
                value = "123"
              )
              val checksumAlgorithm = "sha256"
              val storageManifest = StorageManifest(
                id = bagId,
                source = sourceIdentifier,
                identifiers = Nil,
                manifest = FileManifest(ChecksumAlgorithm(checksumAlgorithm), Nil),
                tagManifest = TagManifest(ChecksumAlgorithm(checksumAlgorithm), Nil),
                locations = Nil,
                Instant.now,
                Instant.now,
                BagVersion(1)
                )
              val putResult = vhs.updateRecord(
                s"${storageManifest.id.space}/${storageManifest.id.externalIdentifier}")(
                ifNotExisting = (storageManifest, EmptyMetadata()))(ifExisting =
                (_, _) => fail("vhs should have been empty!"))
              whenReady(putResult) { _ =>
                val request = HttpRequest(
                  GET,
                  s"$baseUrl/registrar/${storageManifest.id.space.underlying}/${storageManifest.id.externalIdentifier.underlying}")

                whenRequestReady(request) { result =>

                  result.status shouldBe StatusCodes.OK
                  val displayBag = getT[DisplayBag](result.entity)

                  inside(displayBag) { case DisplayBag(
                    actualBagId,
                  DisplayStorageSpace(storageSpaceName, "Space"),
                  DisplayFileManifest(actualChecksumAlgorithm, Nil, "FileManifest"),
                  createdDateString,
                    updatedDateString,
                    1,
                    "Bag") =>
                    actualBagId shouldBe s"${bagId.space.underlying}/${bagId.externalIdentifier.underlying}"
                    storageSpaceName shouldBe bagId.space.underlying
                    actualChecksumAlgorithm shouldBe checksumAlgorithm
                    Instant.parse(createdDateString) shouldBe storageManifest.createdDate
                    Instant.parse(updatedDateString) shouldBe storageManifest.lastModifiedDate
                  }

                }
              }
            }
          }
      }
    }

    it("returns a 404 NotFound if no progress monitor matches id") {
      withConfiguredApp {
        case (_, baseUrl, app) =>
          app.run()

          withActorSystem { implicit actorSystem =>
            val bagId = randomBagId
            val request = Http().singleRequest(
              HttpRequest(
                GET,
                s"$baseUrl/registrar/${bagId.space.underlying}/${bagId.externalIdentifier.underlying}")
            )

            whenReady(request) { result: HttpResponse =>
              result.status shouldBe StatusCodes.NotFound
            }
          }
      }
    }
  }
}
