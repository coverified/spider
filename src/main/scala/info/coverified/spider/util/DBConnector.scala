/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import caliban.client.Operations.{RootMutation, RootQuery}
import caliban.client.{CalibanClientError, SelectionBuilder}
import com.typesafe.scalalogging.LazyLogging
import info.coverified.graphql.schema.CoVerifiedClientSchema.Source.SourceView
import info.coverified.graphql.schema.CoVerifiedClientSchema.Url.UrlView
import info.coverified.graphql.schema.CoVerifiedClientSchema._
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
      : SelectionBuilder[RootQuery, Option[List[Source.SourceView]]] =
    Query.allSources(SourceWhereInput(), skip = 0)(
      Source.view
    )

  def createUrlMutation(
      source: SourceView,
      url: String
  ): SelectionBuilder[RootMutation, Option[UrlView[SourceView]]] = {
    Mutation.createUrl(
      Some(
        UrlCreateInput(
          name = Some(url),
          source = Some(
            SourceRelateToOneInput(
              connect = Some(
                SourceWhereUniqueInput(Some(source.id))
              )
            )
          )
        )
      )
    )(
      Url.view(
        Source.view
      )
    )
  }

  def getUrls(
      url: String,
      apiUrl: Uri,
      authSecret: String
  ): Option[UrlView[SourceView]] = {
    sendRequest(
      sendRequest(
        Query
          .allUrls(
            UrlWhereInput(
              name = Some(url)
            ),
            skip = 0
          )(Url.view(Source.view))
          .toRequest(apiUrl)
          .header("x-coverified-internal-auth", authSecret)
      )
    ).flatMap(_.headOption)
  }

  /**
    * Asking the Connector for all available urls + additional information within the data source
    *
    * @return An effect, that evaluates to a list of [[UrlView]]s
    */
  def getAllSources(
      apiUrl: Uri,
      authSecret: String
  ): ZIO[SttpClient, Throwable, Either[CalibanClientError, List[
    SourceView
  ]]] = {
    sendRequest(
      getAllSourcesRequest
        .toRequest(apiUrl)
        .header("x-coverified-internal-auth", authSecret)
    ).map(_.map(_.getOrElse(List.empty)))
  }

  /**
    * Announce the derived view to API
    *
    * @param mutation Mutation to denote the content
    * @return The equivalent url
    */
  def storeMutation(
      mutation: SelectionBuilder[RootMutation, Option[
        UrlView[SourceView]
      ]],
      apiUrl: Uri,
      authSecret: String
  ): RIO[SttpClient, Either[CalibanClientError, Option[UrlView[SourceView]]]] =
    sendRequest(
      mutation
        .toRequest(apiUrl)
        .header("x-coverified-internal-auth", authSecret)
    )

  private lazy val zioRuntime = zio.Runtime.default

  def sendRequest[T <: Throwable, A](
      request: ZIO[SttpClient, T, Either[T, A]]
  ): A =
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

  private def sendRequest[A](
      req: Request[Either[CalibanClientError, A], Any]
  ): RIO[SttpClient, Either[CalibanClientError, A]] = {
    send(req).map(_.body) // todo error handling
  }
}
