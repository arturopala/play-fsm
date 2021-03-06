resolvers += Resolver.url("HMRC Sbt Plugin Releases", url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(
  Resolver.ivyStylePatterns)
resolvers += "HMRC Releases" at "https://dl.bintray.com/hmrc/releases"

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "1.14.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "1.16.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-artifactory" % "0.17.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.0")

addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.16")

addSbtPlugin("uk.gov.hmrc" % "sbt-play-cross-compilation" % "0.15.0")

val playPlugin =
  if (sys.env.get("PLAY_VERSION").exists(_ == "2.6"))
    "com.typesafe.play" % "sbt-plugin" % "2.6.20"
  else
    "com.typesafe.play" % "sbt-plugin" % "2.5.19"

addSbtPlugin(playPlugin)