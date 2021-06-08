/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import caliban.client.Operations.{RootMutation, RootQuery}
import caliban.client.SelectionBuilder
import com.typesafe.scalalogging.LazyLogging
import info.coverified.graphql.Connector
import info.coverified.graphql.schema.CoVerifiedClientSchema._
import info.coverified.graphql.schema.AllUrlSource.AllUrlSourceView
import info.coverified.graphql.schema.SimpleUrl.SimpleUrlView
import info.coverified.graphql.schema.{AllUrlSource, SimpleUrl}
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}
import sttp.model.Uri
import zio.console.Console
import zio.{RIO, ZIO}

object DBConnector extends LazyLogging {

  private def getAllSourcesRequest
      : SelectionBuilder[RootQuery, Option[List[Option[AllUrlSourceView]]]] = {
    Query.allSources()(
      AllUrlSource.view
    )
  }

  def createUrlMutation(
      source: AllUrlSourceView,
      url: String
  ): SelectionBuilder[RootMutation, Option[SimpleUrlView]] = {
    Mutation.createUrl(
      Some(
        UrlCreateInput(
          name = Some(url),
          source = Some(
            SourceRelateToOneInput(
              connect = Some(
                SourceWhereUniqueInput(source.id)
              )
            )
          )
        )
      )
    )(
      SimpleUrl.view
    )
  }

  /**
    * Asking the Connector for all available urls + additional information within the data source
    *
    * @return An effect, that evaluates to a list of [[SimpleUrlView]]s
    */
  def getAllSources(
      apiUri: Uri
  ): ZIO[Console with SttpClient, Throwable, List[AllUrlSourceView]] = {
    Connector
      .sendRequest {
        getAllSourcesRequest.toRequest(apiUri)
      }
      .map(_.map(_.flatten).getOrElse(List.empty))
  }

  /**
    * Announce the derived view to API
    *
    * @param mutation Mutation to denote the content
    * @return The equivalent url
    */
  def storeMutation(
      mutation: SelectionBuilder[RootMutation, Option[
        SimpleUrlView
      ]],
      apiUri: Uri
  ): RIO[Console with SttpClient, Option[SimpleUrlView]] = {
    Connector.sendRequest(mutation.toRequest(apiUri))
  }

  private lazy val zioRuntime = zio.Runtime.default

  def sendRequest[A](request: ZIO[Console with SttpClient, Throwable, A]): A =
    zioRuntime.unsafeRun(
      request
        .provideCustomLayer(AsyncHttpClientZioBackend.layer())
    )
}
