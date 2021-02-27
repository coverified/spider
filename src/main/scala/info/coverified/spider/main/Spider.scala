/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.main

import caliban.client.Operations.{RootMutation, RootQuery}
import caliban.client.{CalibanClientError, SelectionBuilder}
import info.coverified.graphql.Connector
import info.coverified.graphql.schema.CoVerifiedClientSchema
import info.coverified.graphql.schema.CoVerifiedClientSchema.{
  GeoLocation,
  LocationGoogle,
  Mutation,
  Query,
  Source,
  SourceRelateToOneInput,
  SourceWhereUniqueInput,
  Url,
  UrlCreateInput
}
import sttp.client3.Request
import sttp.client3.asynchttpclient.zio.SttpClient
import sttp.model.Uri
import zio.{CancelableFuture, RIO, URIO, ZIO}
import zio.console.Console

import java.io.File
import java.util.UUID

/**
  * //ToDo: Class Description
  *
  * @version 0.1
  * @since 25.02.21
  */
final case class Spider(apiUrl: Uri, fetchUrlPath: File, tmpDirPath: File) {

  if (!tmpDirPath.exists())
    tmpDirPath.mkdirs()

  def getSources
      : ZIO[Console with SttpClient, Throwable, List[Option[Source.SourceView[
        GeoLocation.GeoLocationView[LocationGoogle.LocationGoogleView]
      ]]]] = {
    // get sources
    val sourcesQuery =
      Query.allSources()(Source.view(GeoLocation.view(LocationGoogle.view)))
    val sourcesReq = Connector
      .sendRequest(sourcesQuery.toRequest(apiUrl))
      .map(_.getOrElse(List.empty))
    sourcesReq
  }

  def getExistingUrls: ZIO[Console with SttpClient, Throwable, List[
    Url.UrlView[Source.SourceView[
      GeoLocation.GeoLocationView[LocationGoogle.LocationGoogleView]
    ]]
  ]] = {
    // get existent urls
    val urlsQuery = Query.allUrls()(
      Url.view(
        Source.view(
          GeoLocation.view(LocationGoogle.view)
        )
      )
    )
    val existingUrls = Connector
      .sendRequest(urlsQuery.toRequest(apiUrl))
      .map(_.map(_.flatten).getOrElse(List.empty))
    existingUrls
  }

  def getMutations(
      source: Source.SourceView[
        GeoLocation.GeoLocationView[LocationGoogle.LocationGoogleView]
      ],
      existingUrls: Seq[Url.UrlView[Source.SourceView[
        GeoLocation.GeoLocationView[LocationGoogle.LocationGoogleView]
      ]]]
  ): ZIO[Console with SttpClient, Throwable, Set[
    Option[Url.UrlView[Source.SourceView[
      GeoLocation.GeoLocationView[LocationGoogle.LocationGoogleView]
    ]]]
  ]] = {
    ZIO.collectAll({
      val outputFileName = source.id

      // run fetchUrls
      source.url.map(
        sourceUrl => fetchUrls(sourceUrl, tmpDirPath, outputFileName)
      )

      // if finished, get the new urls
      val urlsFileSource = scala.io.Source
        .fromFile(s"$tmpDirPath${File.separator}$outputFileName.txt")
      val fetchedUrls = urlsFileSource.getLines().toSet
      urlsFileSource.close()

      // filter the existing urls
      val newUrls = fetchedUrls.filterNot(existingUrls.flatMap(_.url).toSet)

      // create mutations for new urls and send them
      newUrls.map(
        url =>
          Connector.sendRequest(
            Mutation
              .createUrl(
                Some(
                  UrlCreateInput(
                    Some(url),
                    Some(
                      SourceRelateToOneInput(
                        connect = Some(
                          SourceWhereUniqueInput(
                            source.id
                          )
                        )
                      )
                    )
                  )
                )
              )(
                Url.view(
                  Source.view(
                    GeoLocation.view(LocationGoogle.view)
                  )
                )
              )
              .toRequest(apiUrl)
          )
      )
    })
  }

  private def fetchUrls(
      url: String,
      outputPath: File,
      outputFileName: String
  ) = {
    FetchUrlWrapper(
      fetchUrlPath.getAbsolutePath
    ).run(url, outputPath, outputFileName)
  }

}
