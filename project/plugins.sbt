logLevel := Level.Warn
addSbtPlugin("com.dwolla.sbt" %% "sbt-s3-publisher" % "1.2.0")
addSbtPlugin("com.dwolla.sbt" %% "sbt-cloudformation-stack" % "1.2.2")
addSbtPlugin("com.dwolla" % "sbt-assembly-log4j2" % "1.0.0-0e5d5dd98c4c1e12ff7134536456679069c13e4d")
addSbtPlugin("com.dwijnand" % "sbt-travisci" % "1.1.3")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.8")

resolvers ++= Seq(
  Resolver.bintrayIvyRepo("dwolla", "sbt-plugins"),
  Resolver.bintrayRepo("dwolla", "maven")
)
