name         := "dojodis"
version      := "0.1"
scalaVersion := "3.1.3"

lazy val zioVersion  = "2.0.0"
lazy val catsVersion = "2.8.0"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio"            % zioVersion,
  "dev.zio" %% "zio-test"       % zioVersion,
  "dev.zio" %% "zio-test-sbt"   % zioVersion,
  "dev.zio" %% "zio-streams"    % zioVersion,
  "dev.zio" %% "zio-test-junit" % zioVersion
)

scalacOptions ++= Seq(
  "-explain",
  "-encoding",
  "UTF-8",
  "-unchecked",
  "-deprecation",
  "-print-lines"
)

fork / run := true

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
