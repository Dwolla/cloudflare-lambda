logLevel := Level.Warn
addSbtPlugin("com.dwolla.sbt" %% "sbt-s3-publisher" % "1.0.2")
addSbtPlugin("com.dwolla.sbt" %% "sbt-cloudformation-stack" % "1.2.1")

resolvers ++= Seq(
  Resolver.bintrayIvyRepo("dwolla", "sbt-plugins"),
  Resolver.bintrayIvyRepo("dwolla", "maven"),
  Resolver.bintrayRepo("dwolla", "maven")
)