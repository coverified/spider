/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import caliban.client.Operations.{RootMutation, RootQuery}
import caliban.client.{CalibanClientError, SelectionBuilder}
import com.typesafe.scalalogging.LazyLogging
import info.coverified.graphql.schema.AllUrlSource.AllUrlSourceView
import info.coverified.graphql.schema.CoVerifiedClientSchema._
import info.coverified.graphql.schema.SimpleUrl.SimpleUrlView
import info.coverified.graphql.schema.{AllUrlSource, SimpleUrl}
import sttp.client3.Request
import sttp.client3.asynchttpclient.zio.{
  AsyncHttpClientZioBackend,
  SttpClient,
  send
}
import sttp.model.Uri
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
      apiUrl: Uri
  ): ZIO[SttpClient, Throwable, Either[CalibanClientError, List[
    AllUrlSourceView
  ]]] = {
    sendRequest(getAllSourcesRequest.toRequest(apiUrl))
      .map(_.map(_.map(_.flatten).getOrElse(List.empty)))
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
      apiUrl: Uri
  ): ZIO[SttpClient, Throwable, Either[CalibanClientError, Option[
    SimpleUrlView
  ]]] =
    sendRequest(mutation.toRequest(apiUrl))

  private lazy val zioRuntime = zio.Runtime.default

  def sendRequest[T <: Throwable, A](
      request: ZIO[SttpClient, T, Either[T, A]]
  ): A =
    try {
      zioRuntime.unsafeRun(
        request
          .provideCustomLayer(AsyncHttpClientZioBackend.layer())
      ) match {
        case Right(response) =>
          logger.debug(
            "Response: {}",
            response
          )
          response
        case Left(error) =>
          throw error
      }
    }

  private def sendRequest[A](
      req: Request[Either[CalibanClientError, A], Any]
  ): RIO[SttpClient, Either[CalibanClientError, A]] = {
    send(req).map(_.body)
  }
}
