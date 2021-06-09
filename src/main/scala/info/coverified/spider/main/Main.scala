/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.main

import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import info.coverified.spider.Supervisor
import info.coverified.spider.util.DBConnector

import scala.util.{Failure, Success}

object Main extends LazyLogging {

  def main(args: Array[String]): Unit = {

    Config() match {
      case Failure(exception) =>
        logger.error("Parsing config failed.", exception)
        System.exit(1)
      case Success(cfg) =>
        val system = ActorSystem(Supervisor(cfg), "Scraper")

        DBConnector
          .sendRequest(
            DBConnector.getAllSources(cfg.apiUri)
          )
          .foreach { source =>
            system ! Supervisor.Start(source)
          }
//        system ! Supervisor.Start(new URL("https://www.coverified.info/"))
//        system ! Supervisor.Start(new URL("https://paintl.dev.schliflo.de/"))
//        system ! Supervisor.Start(new URL("https://ie3.tu-dortmund.de/"))
//        system ! Supervisor.Start(new URL("https://www.tu-dortmund.de/"))
//        system ! Supervisor.Start(new URL("https://www.bundesregierung.de"))
    }

  }

}
