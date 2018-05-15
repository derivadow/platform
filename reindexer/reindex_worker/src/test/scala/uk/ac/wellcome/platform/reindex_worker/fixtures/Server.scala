package uk.ac.wellcome.platform.reindex_worker.fixtures

import org.scalatest.Suite
import uk.ac.wellcome.monitoring.test.fixtures.CloudWatch
import uk.ac.wellcome.platform.reindex_worker.{Server => AppServer}
import uk.ac.wellcome.test.fixtures.ServerFixtures

trait Server extends ServerFixtures with CloudWatch { this: Suite =>
  def newAppServer: () => AppServer = () => new AppServer()

  val defaultFlags
    : Map[String, String] = Map("aws.region" -> "localhost") ++ cloudWatchLocalFlags
}
