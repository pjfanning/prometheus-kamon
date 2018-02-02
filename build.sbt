name := "prometheus-kamon"

scalaVersion := "2.12.4"

resolvers += Resolver.bintrayRepo("kamon-io", "releases")

libraryDependencies ++= Seq(
  "io.kamon" %% "kamon-core" % "1.0.1",
  "io.prometheus" % "simpleclient" % "0.2.0",
  "io.prometheus" % "simpleclient_common" % "0.2.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)
