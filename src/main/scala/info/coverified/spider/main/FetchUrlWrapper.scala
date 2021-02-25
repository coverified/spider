/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.main

import java.io.File
import scala.language.postfixOps
import scala.sys.process._

/**
  * //ToDo: Class Description
  *
  * @version 0.1
  * @since 25.02.21
  */
case class FetchUrlWrapper(scriptPath: String) {

  def run(url: String, outputPath: File, outputFileName: String): Int = {

    // set script permissions
    "chmod 777 " + scriptPath !

    s"""$scriptPath
       |-n
       |-l $outputPath
       |-f $outputFileName
       |-d $url""".stripMargin !
  }

}
