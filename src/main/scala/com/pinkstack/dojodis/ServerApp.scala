package com.pinkstack.dojodis

import zio.*
import zio.Console.printLine
import zio.ZIO.{attempt, attemptBlocking, fail, fromEither, succeed}
import zio.stream.{Stream, ZPipeline, ZStream}
import utils.*

import java.io.{IOException, PrintWriter}
import java.net.{InetAddress, ServerSocket, Socket as ClientSocket}
import com.pinkstack.dojodis.RESP.*

import java.nio.charset.StandardCharsets

object ServerApp extends zio.ZIOAppDefault:
  def commandHandler(command: Commands): ZIO[Any, Throwable, Reply] =
    command match
      case get: Get         => succeed(Ok())
      case set: Set         => succeed(Ok())
      case exists: Exists   => succeed(Ok())
      case incr: Incr       => succeed(Ok())
      case incrBy: IncrBy   => succeed(Ok())
      case ping: Ping       => succeed(Pong())
      case command: Command => succeed(ArrayOfStrings())

  def connectionHandler_old(connections: Ref[Int])(connection: ZStream.Connection): ZIO[Any, Throwable, Unit] =
    for
      connectionsCount <- connections.updateAndGet(_ + 1)
      _                <- connection.read
        .via(ZPipeline.utf8Decode /* >>> ZPipeline.filter[String](s => !(s.isEmpty || s.isBlank))*/ )
        // .via(ZPipeline.splitOnChunk(_ => Chunk("\\n")))
        // .splitOnChunk(Chunk("\\n"))
        // .via(ZPipeline.splitOn("\\n\\n"))
        .mapZIO(RESP.encodeCommand)
        .mapZIO(commandHandler)
        .catchSome { case e: ParsingError =>
          ZStream.fromIterable(Seq(Error(e.message)))
        }
        // .tap((r: Reply) => succeed(s"⬅️ \"${r}\"").debugThread)
        .mapZIO(RESP.decodeReply)
        .tap(r => succeed(s"⬅️ \"${r.toString.trim}\"").debugThread)
        .via(ZPipeline.utf8Encode)
        // .refineToOrDie[IOException]
        .run(connection.write)
        .debug("handler")
        .unit
        .catchSome { case e: IOException => printLine(e.getMessage) *> ZIO.unit }
        .ensuring(connections.update(_ - 1))
    yield ()

  // def connectionHandler(connections: Ref[Int])(connection: ZStream.Connection): ZIO[Any, Throwable, Unit] =
  //   for _ <- connection.read
  //       .splitOnChunk(Chunk.fromIterable("\r\n".getBytes(StandardCharsets.UTF_8)))
  //       .map(_.map(_.toChar).mkString)
  //       .via(RESP.commandsParser)
  //       .collectType[RESP.SuccessfulCommand]
  //   yield ()

  def getPort = System.env("PORT").map(_.map(_.toInt)).map(_.getOrElse(6666))

  def program =
    for
      port         <- getPort
      _            <- printLine(("🍤" * 10) + s" dojodis @ port ${port} " + ("🍤" * 10))
      connections  <- Ref.make(0)
      socketServer <- ZStream
        .fromSocketServer(port)
        .mapZIOParUnordered(10)(connectionHandler(connections))
        .runDrain
        .fork
      _            <- socketServer.join
    yield ()

  def run = program
