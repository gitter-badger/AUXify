lazy val scala213 = "2.13.0"
lazy val scala212 = "2.12.8" // waiting for semanticdb-scalac for 2.12.9
lazy val scala211 = "2.11.12"
lazy val scala210 = "2.10.7"
lazy val supportedScalaVersions = List(scala213, scala212, scala211, scala210)
lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.8" % Test

ThisBuild / name                 := "auxify"
ThisBuild / organization         := "com.github.dmytromitin"
ThisBuild / organizationName     := "Dmytro Mitin"
ThisBuild / organizationHomepage := Some(url("https://github.com/DmytroMitin"))
ThisBuild / version              := "0.7"
ThisBuild / scalaVersion         := scala213
ThisBuild / scmInfo := Some(ScmInfo(
  url("https://github.com/DmytroMitin/AUXify"),
  "https://github.com/DmytroMitin/AUXify.git"
))
ThisBuild / developers := List(Developer(
  id = "DmytroMitin",
  name = "Dmytro Mitin",
  email = "dmitin3@gmail.com",
  url = url("https://github.com/DmytroMitin")
))
ThisBuild / description := "Library providing macro/meta annotations @aux, @self, @instance, @apply, @delegated, @syntax"
ThisBuild / licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / homepage := Some(url("https://github.com/DmytroMitin/AUXify"))
  // Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishMavenStyle := true
ThisBuild / useGpg := true
ThisBuild / credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credential")
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

lazy val root = (project in file("."))
  .aggregate(macros, macrosTests, metaCore212, metaCore213, metaRules, metaTests, metaUnitTests, syntacticMeta, syntacticMetaTests)
  .settings(
    crossScalaVersions := Nil,
    publish / skip := true,
  )

// ======================= MACROS ================================

lazy val macrosCommonSettings = Seq(
  crossScalaVersions := supportedScalaVersions,
  scalacOptions ++= (
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v >= 13 => Seq("-Ymacro-annotations")
      case _                       => Nil
    }) ++ Seq(
//    "-Ymacro-debug-lite",
//    "-Ymacro-debug-verbose",
//    "-Ydebug",
//    "-Xprint:typer",
//    "-Xprint-types",
  ),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  ),
  libraryDependencies ++= (
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v >= 13 => Seq(
        "org.typelevel" % "macro-compat_2.13.0-RC2" % "1.1.1",
      )
      case _                       => Seq(
        compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),
        "org.typelevel" %% "macro-compat" % "1.1.1",
      )
    }
  ) ++ Seq(
    scalaOrganization.value % "scala-compiler" % scalaVersion.value % Provided, // for macro-compat
  )
)

lazy val macros = (project in file("macros")).settings(
  name := "auxify-macros",
  libraryDependencies += scalaOrganization.value % "scala-reflect" % scalaVersion.value,
  macrosCommonSettings,
)

lazy val macrosTests = (project in file("macros-tests")).dependsOn(macros).settings(
  name := "auxify-macros-tests",
  libraryDependencies += scalaTest,
  macrosCommonSettings,
  publish / skip := true,
)

// ======================= META ================================

lazy val V = _root_.scalafix.sbt.BuildInfo

lazy val metaCommonSettings = Seq(
  crossScalaVersions := Seq(V.scala212),
  scalaVersion := V.scala212,
  addCompilerPlugin(scalafixSemanticdb),
  scalacOptions ++= List(
    "-Yrangepos",
    "-P:semanticdb:synthetics:on",
  )
)

lazy val metaCore212 = (project in file("meta-core-2.12"))
  .settings(
    name := "auxify-meta-core",
    scalaSource in Compile := baseDirectory.value / ".." / "meta-core" / "src" / "main" / "scala",
    metaCommonSettings
  )

lazy val metaRules = (project in file("meta"))
  .settings(
    name := "auxify-meta",
    libraryDependencies ++= Seq(
      "ch.epfl.scala" %% "scalafix-core" % V.scalafixVersion,
    ),
    metaCommonSettings
  )

lazy val metaIn = (project in file("meta-in"))
  .dependsOn(metaCore212)
  .settings(
    name := "auxify-meta-in",
    publish / skip := true,
    metaCommonSettings
  )

lazy val metaOut = (project in file("meta-out"))
  .dependsOn(metaCore212) // for import and if meta annotation is not expanded // TODO #15
  .settings(
    name := "auxify-meta-out",
    sourceGenerators.in(Compile) += Def.taskDyn {
      val root = baseDirectory.in(ThisBuild).value.toURI.toString
      val from = sourceDirectory.in(metaIn, Compile).value
      val to = sourceManaged.in(Compile).value
      val outFrom = from.toURI.toString.stripSuffix("/").stripPrefix(root)
      val outTo = to.toURI.toString.stripSuffix("/").stripPrefix(root)
      Def.task {
        scalafix
          .in(metaIn, Compile)
          .toTask(s" --rules=file:meta/src/main/scala/com/github/dmytromitin/auxify/meta/AuxRule.scala --out-from=$outFrom --out-to=$outTo")
          .value
        (to ** "*.scala").get
      }
    }.taskValue,
    publish / skip := true,
    metaCommonSettings
  )

lazy val metaOutExpectedForTests = (project in file("meta-out-expected-for-tests"))
  .dependsOn(metaCore212) // for import statement and if meta annotation is not expanded // TODO #15
  .settings(
    name := "auxify-out-expected-for-tests",
    skip in publish := true,
    metaCommonSettings
  )

// config file meta/src/main/resources/META-INF/services/scalafix.v1.Rule and top comment /* SomeRule */
// in meta-in/src/main/scala/[package]/[input file].scala are necessary for tests,
// for code generation they are not necessary
lazy val metaTests = (project in file("meta-tests"))
  .dependsOn(metaRules)
  .settings(
    name := "auxify-meta-tests",
    skip in publish := true,
    libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % V.scalafixVersion % Test cross CrossVersion.full,
    compile.in(Compile) :=
      compile.in(Compile).dependsOn(compile.in(metaIn, Compile)).value,
    scalafixTestkitOutputSourceDirectories :=
      sourceDirectories.in(metaOutExpectedForTests, Compile).value,
    scalafixTestkitInputSourceDirectories :=
      sourceDirectories.in(metaIn, Compile).value,
    scalafixTestkitInputClasspath :=
      fullClasspath.in(metaIn, Compile).value,
    metaCommonSettings
  )
  .enablePlugins(ScalafixTestkitPlugin)

lazy val metaUnitTests = (project in file("meta-unit-tests"))
  .dependsOn(metaOut)
  .settings(
    name := "auxify-meta-tests",
    publish / skip := true,
    libraryDependencies += scalaTest,
    metaCommonSettings
  )

// ======================= SYNTACTIC META ================================

lazy val syntacticMetaCommonSettings = Seq(
  crossScalaVersions := Seq(scala213, scala212),
  scalaVersion := scala213,
)

lazy val metaCore213 = (project in file("meta-core-2.13"))
  .settings(
    name := "auxify-meta-core",
    scalaSource in Compile := baseDirectory.value / ".." / "meta-core" / "src" / "main" / "scala",
    syntacticMetaCommonSettings,
  )

lazy val syntacticMeta = (project in file("syntactic-meta"))
  .settings(
    name := "auxify-syntactic-meta",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % "4.2.0",
    ),
    syntacticMetaCommonSettings,
  )

lazy val syntacticMetaIn = (project in file("syntactic-meta-in"))
  .dependsOn(metaCore213)
  .settings(
    name := "auxify-syntactic-meta-in",
    syntacticMetaCommonSettings,
    skip in publish := true,
  )

lazy val syntacticMetaOut = (project in file("syntactic-meta-out"))
  .dependsOn(metaCore213) // for import statement and if meta annotation is not expanded // TODO #15
  .settings(
    name := "auxify-syntactic-meta-out",
    sourceGenerators in Compile += Def.task {
      import com.github.dmytromitin.auxify.meta.syntactic.Generator
      Generator.gen(
        inputDir  = sourceDirectory.in(syntacticMetaIn, Compile).value,
        outputDir = sourceManaged.in(Compile).value
      )
    }.taskValue,
    syntacticMetaCommonSettings,
    skip in publish := true,
  )

lazy val syntacticMetaTests = (project in file("syntactic-meta-tests"))
  .dependsOn(syntacticMetaOut)
  .settings(
    name := "auxify-syntactic-meta-tests",
    libraryDependencies += scalaTest,
    syntacticMetaCommonSettings,
    skip in publish := true,
  )
