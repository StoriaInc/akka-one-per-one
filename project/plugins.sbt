resolvers ++= Seq("Frumatic Public" at "http://nexus.frumatic.com/content/groups/public/",
Resolver.url("Frumatic Public Ivy", url("http://nexus.frumatic.com/content/groups/public/"))(Resolver.ivyStylePatterns))

//"Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
//"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
//"Typesafe Snaphot Repository" at "http://repo.typesafe.com/typesafe/snapshots/")

addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.3.4")

addSbtPlugin("com.typesafe.akka" % "akka-sbt-plugin" % "2.2.0")
