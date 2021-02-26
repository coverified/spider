/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.main

import com.typesafe.scalalogging.LazyLogging
import scopt.{OptionParser => scoptOptionParser}

/**
  * //ToDo: Class Description
  *
  * @version 0.1
  * @since 26.02.21
  */
object ArgsParser extends LazyLogging {

  final case class Args(
      apiUrl: Option[String] = None,
      fetchUrlScriptPath: Option[String] = None
  )

  private def buildParser: scoptOptionParser[Args] = {
    new scoptOptionParser[Args]("CoVerifiedSpider") {
      opt[String]("apiUrl")
        .action((value, args) => {
          args.copy(
            apiUrl = Option(value)
          )
        })
        .validate(
          value =>
            if (value.trim.isEmpty) failure("apiUrl cannot be empty!")
            else success
        )
        .text("Backend API Url")
        .minOccurs(1)
      opt[String]("scriptPath")
        .action((value, args) => {
          args.copy(
            fetchUrlScriptPath = Option(value)
          )
        })
        .validate(
          value =>
            if (value.trim.isEmpty)
              failure("fetchUrl script path cannot be empty!")
            else success
        )
        .text("FetchUrl script full path")
        .minOccurs(1)

    }

  }

  def parse(args: Array[String]): Option[Args] =
    buildParser.parse(args, init = Args())

}
