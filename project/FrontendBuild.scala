import sbt._

object FrontendBuild extends Build with MicroService {
  val appName = "ers-returns-frontend"
  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  import play.core.PlayVersion
  import play.sbt.PlayImport._


  private val playPartialVersion = "6.5.0"
  private val httpCachingVersion = "8.3.0"
  private val domainVersion = "4.1.0"
  private val frontendBootstrapVersion = "12.9.0"
  private val hmrcTestVersion = "3.3.0"
  private val scalaTestVersion = "3.0.4"
  private val pegdownVersion = "1.6.0"
  private val pdfboxVersion = "1.8.16"
  private val xmpboxVersion = "1.8.16"
  private val scalaParserCombinatorsVersion = "1.0.7"
  private val scalatestVersion = "3.0.8"
  private val scalatestPlusPlayVersion = "2.0.1"
  private val jsoupVersion = "1.9.2"
  private val mockitoCoreVersion = "1.9.5"

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "play-partials" % playPartialVersion,
    "uk.gov.hmrc" %% "frontend-bootstrap" % frontendBootstrapVersion,
    "uk.gov.hmrc" %% "domain" % domainVersion,
    "uk.gov.hmrc" %% "http-caching-client" % httpCachingVersion,
    "uk.gov.hmrc" %% "play-language" % "3.4.0",
		"uk.gov.hmrc" %% "auth-client" % "2.32.0-play-25",
    "org.scala-lang.modules" %% "scala-parser-combinators" % scalaParserCombinatorsVersion,
    "org.apache.pdfbox" % "pdfbox" % pdfboxVersion,
    "org.apache.pdfbox" % "xmpbox" % xmpboxVersion
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply(): Seq[ModuleID] = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatest" %% "scalatest" % scalatestVersion % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % scalatestPlusPlayVersion % scope,
        "org.pegdown" % "pegdown" % pegdownVersion % scope,
        "org.jsoup" % "jsoup" % jsoupVersion % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "org.mockito" % "mockito-core" % "3.1.0" % scope,
        "com.github.tomakehurst" % "wiremock-standalone" % "2.25.1" % scope
      )
    }.test
  }

  def apply(): Seq[ModuleID] = compile ++ Test()
}
