/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.main

import info.coverified.graphql.schema.CoVerifiedClientSchema.Url
import sttp.client3.UriContext
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}
import zio.console.Console
import zio.{App, ExitCode, RIO, Task, URIO, ZIO}

/**
  * //ToDo: Class Description
  *
  * @version 0.1
  * @since 25.02.21
  */
object Run extends App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    // todo delete tmp files

    val spider = Spider(
      uri"http://coverified-backend-keystone.docker/admin/api",
      new java.io.File(".")
    )

    val spiderRun = for {
      sources <- spider.getSources
      existingUrls <- spider.getExistingUrls
      _ <- spider.getMutations(sources, existingUrls)
    } yield ()

    spiderRun.provideCustomLayer(AsyncHttpClientZioBackend.layer()).exitCode

  }
}
