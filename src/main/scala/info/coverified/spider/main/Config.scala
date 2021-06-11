/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.main

import com.typesafe.scalalogging.LazyLogging
import sttp.model.Uri

import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.concurrent.duration._
import scala.language.postfixOps

final case class Config(
    scrapeParallelism: Int,
    scrapeInterval: FiniteDuration,
    scrapeTimeout: FiniteDuration,
    shutdownTimeout: FiniteDuration,
    maxRetries: Int,
    apiUrl: Uri,
    authSecret: String
)

object Config extends LazyLogging {

  private val SCRAPE_PARALLELISM = "SCRAPE_PARALLELISM"
  private val SCRAPE_INTERVAL = "SCRAPE_INTERVAL"
  private val SCRAPE_TIMEOUT = "SCRAPE_TIMEOUT"
  private val SHUTDOWN_TIMEOUT = "SHUTDOWN_TIMEOUT"
  private val MAX_RETRIES = "MAX_RETRIES"
  private val API_URL = "API_URL"
  private val AUTH_SECRET = "AUTH_SECRET"

  // all time values in milliseconds
  private val defaultParams: Map[String, String] = Map(
    SCRAPE_PARALLELISM -> 100.toString,
    SCRAPE_INTERVAL -> 500.toString,
    SCRAPE_TIMEOUT -> 20000.toString,
    SHUTDOWN_TIMEOUT -> 15000.toString,
    MAX_RETRIES -> 0.toString,
    API_URL -> "",
    AUTH_SECRET -> ""
  )

  private val envParams: Map[String, String] =
    defaultParams.keys
      .zip(defaultParams.values)
      .map {
        case (envParam, defaultVal) =>
          envParam -> sys.env
            .getOrElse(envParam, {
              logger.warn(
                s"No env value provided for param $envParam. Fallback to default value: $defaultVal "
              )
              defaultVal
            })
      }
      .toMap

  def apply(): Try[Config] = {
    Try {
      new Config(
        envParams(SCRAPE_PARALLELISM).toInt,
        envParams(SCRAPE_INTERVAL).toInt millis,
        envParams(SCRAPE_TIMEOUT).toInt millis,
        envParams(SHUTDOWN_TIMEOUT).toInt millis,
        envParams(MAX_RETRIES).toInt,
        Uri.unsafeParse(envParams(API_URL)),
        envParams(AUTH_SECRET)
      )
    }
  }

}
