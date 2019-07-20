# AUXify

[![Build Status](https://travis-ci.org/DmytroMitin/AUXify.svg?branch=master)](https://travis-ci.org/DmytroMitin/AUXify)

## Using
Write in `build.sbt`
```scala
scalaVersion := "2.13.0"
//scalaVersion := "2.12.8"
//scalaVersion := "2.11.12"
//scalaVersion := "2.10.7"

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("staging"),
  "Sonatype Staging" at "https://oss.sonatype.org/service/local/staging/deployByRepositoryId/comgithubdmytromitin-1000"
)

credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credential")

libraryDependencies += "com.github.dmytromitin" %% "auxify-macros" % "0.1"

scalacOptions += "-Ymacro-annotations" // in Scala >= 2.13
//addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full) // in Scala <= 2.12
```

## @Aux
Transforms
```scala
@Aux
trait Add[N <: Nat, M <: Nat] {
  type Out <: Nat
  def apply(n: N, m: M): Out
}

object Add {
  //...
}
```
into
```scala
trait Add[N <: Nat, M <: Nat] {
  type Out <: Nat
  def apply(n: N, m: M): Out
}

object Add {
  type Aux[N <: Nat, M <: Nat, Out0 <: Nat] = Add[N, M] { type Out = Out0 }
  
  //...
}
```

So it can be used:
```scala
implicitly[Add.Aux[_2, _3, _5]]
```

## @This
Transforms
```scala
@This
sealed trait Nat {
  type ++ = Succ[This]
}

@This
case object _0 extends Nat 

type _0 = _0.type

@This
case class Succ[N <: Nat](n: N) extends Nat
```
into
```scala
sealed trait Nat { self =>
  type This >: this.type <: Nat { type This = self.This }
  type ++ = Succ[This]
}

case object _0 extends Nat {
  override type This = _0
}

type _0 = _0.type

case class Succ[N <: Nat](n: N) extends Nat {
  override type This = Succ[N]
}
```

Generating lower bound `>: this.type` and/or F-bound `type This = self.This` for trait can be switched off
```scala
@This(lowerBound = false, fBound = false)
```