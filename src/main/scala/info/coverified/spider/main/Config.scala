/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.main

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.concurrent.duration._
import scala.language.postfixOps

final case class Config(
    scrapeParallelism: Int,
    scrapeInterval: FiniteDuration,
    scrapeTimeout: Int,
    shutdownTimeout: FiniteDuration,
    maxRetries: Int
)

object Config extends LazyLogging {

  private val SCRAPE_PARALLELISM = "SCRAPE_PARALLELISM"
  private val SCRAPE_INTERVAL = "SCRAPE_INTERVAL"
  private val SCRAPE_TIMEOUT = "SCRAPE_TIMEOUT"
  private val SHUTDOWN_TIMEOUT = "SHUTDOWN_TIMEOUT"
  private val MAX_RETRIES = "MAX_RETRIES"

  // all time values in milliseconds
  private val defaultParams: Map[String, Int] = Map(
    SCRAPE_PARALLELISM -> 100,
    SCRAPE_INTERVAL -> 500,
    SCRAPE_TIMEOUT -> 20000,
    SHUTDOWN_TIMEOUT -> 15000,
    MAX_RETRIES -> 0
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
            .toString
      }
      .toMap

  def apply(): Try[Config] = {
    Try {
      new Config(
        envParams(SCRAPE_PARALLELISM).toInt,
        envParams(SCRAPE_INTERVAL).toInt millis,
        envParams(SCRAPE_TIMEOUT).toInt,
        envParams(SHUTDOWN_TIMEOUT).toInt millis,
        envParams(MAX_RETRIES).toInt
      )
    }
  }

}
