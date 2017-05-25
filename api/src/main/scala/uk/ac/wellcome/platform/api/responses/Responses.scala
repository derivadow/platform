package uk.ac.wellcome.platform.api.responses

import com.fasterxml.jackson.annotation.{JsonProperty, JsonUnwrapped}
import scala.language.existentials


case class ResultResponse(
  @JsonProperty("@context") context: String,
  @JsonUnwrapped result: Any
)

case class ResultListResponse(
  @JsonProperty("@context") context: String,
  pageSize: Int = 10,
  totalPages: Int = 10,
  totalResults: Int = 100,
  results: Array[_ <: Any]
) {
  @JsonProperty("type") val ontologyType: String = "ResultList"
}
