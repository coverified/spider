/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.main

import com.typesafe.scalalogging.LazyLogging
import info.coverified.graphql.schema.CoVerifiedClientSchema.Url
import info.coverified.spider.main.ArgsParser.Args
import sttp.client3.UriContext
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}
import zio.console.Console
import zio.{App, ExitCode, RIO, Task, URIO, ZIO}

import java.io.File

/**
  * //ToDo: Class Description
  *
  * @version 0.1
  * @since 25.02.21
  */
object Run extends App with LazyLogging {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val spider = ArgsParser
      .parse(args.toArray)
      .flatMap {
        case Args(Some(apiUrl), Some(fetchUrlPath)) =>
          Some(
            Spider(
              uri"$apiUrl",
              new File(fetchUrlPath),
              new java.io.File(".")
            )
          )
        case _ =>
          logger.info(
            "Trying to get configuration from environment variables ... "
          )
          None
      }
      .getOrElse(
        Option(sys.env("SPIDER_API_URL"))
          .zip(Option(sys.env("SPIDER_FETCH_SCRIPT_PATH"))) match {
          case Some((apiUrl, fetchUrlPath)) =>
            Spider(
              uri"$apiUrl",
              new File(fetchUrlPath),
              new java.io.File(".")
            )
          case None =>
            throw new RuntimeException(
              "Config parameters missing!"
            )
        }
      )

    // todo delete tmp files
    val spiderRun = for {
      sources <- spider.getSources
      existingUrls <- spider.getExistingUrls
      _ <- ZIO.collectAll(
        sources.flatten.map(source => spider.getMutations(source, existingUrls))
      )
    } yield ()

    spiderRun.provideCustomLayer(AsyncHttpClientZioBackend.layer()).exitCode

  }
}
