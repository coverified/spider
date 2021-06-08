/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.graphql.schema

import caliban.client.SelectionBuilder
import info.coverified.graphql.schema.CoVerifiedClientSchema.Source
import info.coverified.graphql.schema.CoVerifiedClientSchema.Source.urls

object AllUrlSource {

  /**
    * Little sister of [[Source]], but with pre-set url selection without resolving meta information
    *
    * @param id      Identifier
    * @param name    Name of source
    * @param acronym Acronym of the source
    * @param url     Base url
    */
  final case class AllUrlSourceView(
      id: String,
      name: Option[String],
      acronym: Option[String],
      url: String,
      urls: List[String]
  )

  def view: SelectionBuilder[Source, AllUrlSourceView] =
    (Source.id ~ Source.name ~ Source.acronym ~ Source.url ~ urls(
      )(SimpleUrl.view)).map {
      case ((((id, name), acronym), url), urls) =>
        AllUrlSourceView(
          id,
          name,
          acronym,
          url.getOrElse(throw new RuntimeException()),
          urls.flatMap(_.name)
        )
    }
}
