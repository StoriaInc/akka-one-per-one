import sbt._
import Keys._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.{ MultiJvm }
import com.typesafe.sbt.SbtMultiJvm._
import akka.sbt.AkkaKernelPlugin
import akka.sbt.AkkaKernelPlugin.{ Dist, outputDirectory, distJvmOptions}


object ApplicationBuild extends Build
{
  def frumaticRepository(r : String) : Resolver =
    "Sonatype Nexus Repository Manager" at "http://nexus.frumatic.com/content/repositories/" + r
  val frumaticRepositorySnapshots = frumaticRepository("snapshots")
  val frumaticRepositoryReleases = frumaticRepository("releases")
  val frumaticTypesafeSnapshots = frumaticRepository("typesafe-snapshots")
  val frumaticTypesafeReleases = frumaticRepository("typesafe")


	val appName       = "one-for-one"
  val AkkaVersion   = "2.2.0-RC1"
  val scalaVer      = "2.10.2"
  val isSnapshot    = true
  val version       = "1.0" + (if (isSnapshot) "-SNAPSHOT" else "")

  lazy val multiJvmSettings = SbtMultiJvm.multiJvmSettings ++ Seq(
    // make sure that MultiJvm test are compiled by the default test compilation
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
    // disable parallel tests
    parallelExecution in Test := false,
    // make sure that MultiJvm tests are executed by the default test target
    executeTests in Test <<=
      ((executeTests in Test), (executeTests in MultiJvm)) map {
        case ((_, testResults), (_, multiJvmResults)) =>
          val results = testResults ++ multiJvmResults
          (Tests.overall(results.values), results)
      })

	val buildSettings = Defaults.defaultSettings ++ multiJvmSettings ++
    Seq (
      organization := "codebranch",
      Keys.version := version,
      scalaVersion := scalaVer,
      retrieveManaged := true,
	    //scalacOptions ++= Seq("-feature"),
      testOptions in Test := Nil,
      resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
      publishTo := {
        if (isSnapshot)
          Some(frumaticRepositorySnapshots)
        else
          Some(frumaticRepositoryReleases)
      },
      credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
      libraryDependencies ++= appDependencies,
	    exportJars := true
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
    "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion,
    "org.scalatest" %% "scalatest" % "1.9" % "test"
	)

	val main = Project(
		appName,
		file("."),
		settings = buildSettings)
    .configs(MultiJvm)
}
