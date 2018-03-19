package uk.ac.wellcome.elasticsearch

import com.google.inject.Inject
import com.sksamuel.elastic4s.analyzers._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.mappings.{FieldDefinition, MappingDefinition}
import com.sksamuel.elastic4s.mappings.dynamictemplate.DynamicMapping
import com.twitter.inject.Logging
import com.twitter.inject.annotations.Flag

class WorksIndex @Inject()(client: HttpClient,
                           @Flag("es.index") name: String,
                           @Flag("es.type") itemType: String)
    extends ElasticSearchIndex
    with Logging {

  val rootIndexType = itemType

  val httpClient: HttpClient = client
  val indexName = name

  val license = objectField("license").fields(
    keywordField("ontologyType"),
    keywordField("licenseType"),
    textField("label"),
    textField("url")
  )

  val sourceIdentifier = objectField("sourceIdentifier")
    .fields(
      keywordField("ontologyType"),
      keywordField("identifierScheme"),
      keywordField("value")
    )

  val identifiers = objectField("identifiers")
    .fields(
      keywordField("ontologyType"),
      keywordField("identifierScheme"),
      keywordField("value")
    )

  def location(fieldName: String = "locations") =
    objectField(fieldName).fields(
      keywordField("type"),
      keywordField("ontologyType"),
      keywordField("locationType"),
      keywordField("label"),
      textField("url"),
      textField("credit"),
      license
    )

  def date(fieldName: String) = objectField(fieldName).fields(
    textField("label"),
    keywordField("ontologyType")
  )

  def labelledTextField(fieldName: String) = objectField(fieldName).fields(
    textField("label"),
    keywordField("ontologyType")
  )

  val items = objectField("items").fields(
    keywordField("canonicalId"),
    sourceIdentifier,
    identifiers,
    location(),
    booleanField("visible"),
    keywordField("ontologyType")
  )
  val publishers = objectField("publishers").fields(
    textField("label"),
    keywordField("type"),
    keywordField("ontologyType")
  )

  val language = objectField("language").fields(
    keywordField("id"),
    textField("language"),
    keywordField("ontologyType")
  )

  val rootIndexFields: Seq[FieldDefinition with Product with Serializable] =
    Seq(
      keywordField("canonicalId"),
      booleanField("visible"),
      keywordField("ontologyType"),
      intField("version"),
      sourceIdentifier,
      identifiers,
      textField("title").fields(
        textField("english").analyzer(EnglishLanguageAnalyzer)),
      textField("description").fields(
        textField("english").analyzer(EnglishLanguageAnalyzer)),
      textField("physicalDescription").fields(
        textField("english").analyzer(EnglishLanguageAnalyzer)),
      textField("extent").fields(
        textField("english").analyzer(EnglishLanguageAnalyzer)),
      textField("lettering").fields(
        textField("english").analyzer(EnglishLanguageAnalyzer)),
      date("createdDate"),
      labelledTextField("creators"),
      labelledTextField("subjects"),
      labelledTextField("genres"),
      labelledTextField("placesOfPublication"),
      items,
      publishers,
      date("publicationDate"),
      language,
      location("thumbnail")
    )

  val mappingDefinition: MappingDefinition = mapping(rootIndexType)
    .dynamic(DynamicMapping.Strict)
    .as(rootIndexFields)
}
