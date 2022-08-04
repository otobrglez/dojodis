package com.pinkstack.dojodis

import zio.*
import zio.Console.printLine
import zio.ZIO.{attempt, attemptBlocking, fail, fromEither, succeed}
import zio.stream.{Stream, ZPipeline, ZStream}
import utils.*
import java.io.{IOException, PrintWriter}
import java.net.{InetAddress, ServerSocket, Socket as ClientSocket}
import com.pinkstack.dojodis.RESP.*

object ServerAppV3 extends zio.ZIOAppDefault:
  def commandHandler(command: Commands): ZIO[Any, Throwable, Reply] =
    command match
      case get: Get         => succeed(Ok())
      case set: Set         => succeed(Ok())
      case exists: Exists   => succeed(Ok())
      case incr: Incr       => succeed(Ok())
      case incrBy: IncrBy   => succeed(Ok())
      case ping: Ping       => succeed(Pong())
      case command: Command => succeed(ArrayOfStrings())

  def connectionHandler(connections: Ref[Int])(connection: ZStream.Connection): ZIO[Any, Throwable, Unit] =
    for
      connectionsCount <- connections.updateAndGet(_ + 1)
      _                <- connection.read
        .via(ZPipeline.utf8Decode)
        .filterNot(s => s.trim.isBlank || s.trim.isEmpty)
        .tap(s => succeed(s"➡️ Got: \"${s.replaceAll("\\r\\n", "---")}\"").debugThread)
        .mapZIO(RESP.encodeCommand)
        .mapZIO(commandHandler)
        .catchSome { case e: ParsingError =>
          ZStream.fromIterable(Seq(Error(e.message)))
        }
        .tap((r: Reply) => succeed(s"⬅️ \"${r}\"").debugThread)
        .mapZIO(RESP.decodeReply)
        .tap(r => succeed(s"⬅️ \"${r.toString.trim}\"").debugThread)
        .via(ZPipeline.utf8Encode)
        .refineToOrDie[IOException]
        .run(connection.write)
        .unit
        .catchSome { case e: IOException => printLine(e.getMessage) *> ZIO.unit }
        .ensuring(connections.update(_ - 1))
    yield ()

  def server =
    for
      connections <- Ref.make(0)
      ss          <- ZStream
        .fromSocketServer(6666)
        .mapZIOParUnordered(10)(connectionHandler(connections))
        .runDrain
        .fork
      _           <- ss.join
    yield ()

  def program =
    for
      _   <- printLine("Booting BetterApp")
      fib <- server.fork
      _   <- fib.join
    yield ()

  def run =
    program
