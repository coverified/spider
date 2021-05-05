/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.main

import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.concurrent.duration._
import scala.language.postfixOps

final case class Config(
    scrapParallelism: Int,
    scrapeInterval: FiniteDuration,
    scrapeTimeout: Timeout,
    shutdownTimeout: FiniteDuration
)

object Config extends LazyLogging {

  private val SCRAPE_PARALLELISM = "SCRAPE_PARALLELISM"
  private val SCRAPE_INTERVAL = "SCRAPE_INTERVAL"
  private val SCRAPE_TIMEOUT = "SCRAPE_TIMEOUT"
  private val SHUTDOWN_TIMEOUT = "SHUTDOWN_TIMEOUT"

  // all time values in seconds
  private val defaultParams: Map[String, Int] = Map(
    SCRAPE_PARALLELISM -> 10,
    SCRAPE_INTERVAL -> 1,
    SCRAPE_TIMEOUT -> 3,
    SHUTDOWN_TIMEOUT -> 10
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
        envParams(SCRAPE_INTERVAL).toInt seconds,
        envParams(SCRAPE_TIMEOUT).toInt seconds,
        envParams(SHUTDOWN_TIMEOUT).toInt seconds
      )
    }
  }

}
