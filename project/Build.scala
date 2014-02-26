import sbt._
import Keys._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.{ MultiJvm }
import com.typesafe.sbt.SbtMultiJvm._
import akka.sbt.AkkaKernelPlugin
import akka.sbt.AkkaKernelPlugin.{ Dist, outputDirectory, distJvmOptions}


object ApplicationBuild extends Build
{
  val frumaticRepositoryURLPrefix = "http://nexus.frumatic.com/content"
  val frumaticPublicRepositoryURL = frumaticRepositoryURLPrefix + "/groups/public/"
  val frumaticPublicRepository : Resolver = "Frumatic Public" at frumaticPublicRepositoryURL
  val frumaticPublicIvyRepository : Resolver =
    Resolver.url("Frumatic Public Ivy", url(frumaticPublicRepositoryURL))(Resolver.ivyStylePatterns)

  def frumaticRepository(r : String) : Resolver =
    "Sonatype Nexus Repository Manager" at frumaticRepositoryURLPrefix + "/repositories/" + r
  val frumaticRepositorySnapshots = frumaticRepository("snapshots")
  val frumaticRepositoryReleases = frumaticRepository("releases")

  val resolvers = Seq(frumaticPublicRepository, frumaticPublicIvyRepository)

	val appName       = "one-per-one"
  val AkkaVersion   = "2.3.0-RC4"
  val scalaVer      = "2.10.3"
  val isSnapshot    = true
  val version       = "1.2.0" + (if (isSnapshot) "-SNAPSHOT" else "")

  lazy val multiJvmSettings = SbtMultiJvm.multiJvmSettings ++ Seq(
    // make sure that MultiJvm test are compiled by the default test compilation
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
    // disable parallel tests
    parallelExecution in Test := false
//    // make sure that MultiJvm tests are executed by the default test target
//    executeTests in Test <<=
//      ((executeTests in Test), (executeTests in MultiJvm)) map {
//		  case (testResults, multiJvmResults) =>
//		  	Tests.Output(
//		  		Tests.overall(List(testResults.overall, multiJvmResults.overall)),
//				testResults.events ++ multiJvmResults.events,
//				testResults.summaries ++ multiJvmResults.summaries)
//      }
    )

	val buildSettings = Defaults.defaultSettings ++ multiJvmSettings ++
    Seq (
      organization := "codebranch",
      Keys.version := version,
      scalaVersion := scalaVer,
      retrieveManaged := true,
	    //scalacOptions ++= Seq("-feature"),
      testOptions in Test := Nil,
      Keys.resolvers ++= resolvers,
      publishTo := {
        if (isSnapshot)
          Some(frumaticRepositorySnapshots)
        else
          Some(frumaticRepositoryReleases)
      },
      credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
      libraryDependencies ++= appDependencies
	    //exportJars := true
    )

	val appDependencies = Seq(
    "com.typesafe" % "config" % "1.0.0",
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-remote" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % AkkaVersion,
    "com.typesafe.akka" %% "akka-contrib" % AkkaVersion,

	//Logging
	  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
		"ch.qos.logback" % "logback-classic" % "1.0.9",

  //Testing
    "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % "test",
    "org.scalatest" %% "scalatest" % "1.9" % "test"
	)

	val main = Project(
		appName,
		file("."),
		settings = buildSettings)
    .configs(MultiJvm)
}
