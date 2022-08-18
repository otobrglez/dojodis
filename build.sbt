name         := "dojodis"
version      := "0.1"
scalaVersion := "3.1.3"

lazy val zioVersion  = "2.0.0"
lazy val catsVersion = "2.8.0"

libraryDependencies ++= Seq(
  "dev.zio"                      %% "zio"                 % zioVersion,
  "dev.zio"                      %% "zio-test"            % zioVersion,
  "dev.zio"                      %% "zio-test-sbt"        % zioVersion,
  "dev.zio"                      %% "zio-streams"         % zioVersion,
  "dev.zio"                      %% "zio-test-junit"      % zioVersion,
  "org.typelevel"                %% "shapeless3-deriving" % "3.1.0",
  "com.softwaremill.magnolia1_3" %% "magnolia"            % "1.1.4"
)

scalacOptions ++= Seq(
  "-explain",
  "-encoding",
  "UTF-8",
  "-unchecked",
  "-deprecation",
  "-print-lines",
  "-Yretain-trees"
)

fork / run := true

testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
