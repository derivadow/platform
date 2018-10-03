package uk.ac.wellcome.platform.archive.archivist.flow

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.PublishResult
import uk.ac.wellcome.messaging.sns.SNSConfig
import uk.ac.wellcome.platform.archive.common.flows.SnsPublishFlow
import uk.ac.wellcome.platform.archive.common.models.ArchiveComplete

object ArchiveCompleteFlow {
  def apply(snsConfig: SNSConfig)(implicit snsClient: AmazonSNS)
    : Flow[ArchiveComplete, PublishResult, NotUsed] =
    Flow[ArchiveComplete]
      .via(SnsPublishFlow[ArchiveComplete](snsClient, snsConfig))
      .log("published notification")
}
