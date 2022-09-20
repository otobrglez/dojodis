/*
 * Copyright 2022 Oto Brglez - <otobrglez@gmail.com>
 */

package com.pinkstack.dojodis

import com.pinkstack.dojodis.RESP.*
import com.pinkstack.dojodis.commands.{Hacks, Strings}
import zio.Console.printLine
import zio.ZIO.succeed
import zio.stm.TMap
import zio.stream.{Stream, ZPipeline, ZStream}
import zio.{Ref, ZIO}

import java.io.IOException

type DataMap     = TMap[String, String]
type Connections = Ref[Int]

object ServerApp extends zio.ZIOAppDefault:
  def commandHandlers(dataMap: DataMap)(command: Commands): ZIO[Any, Throwable, Reply] =
    (Strings.handler orElse Hacks.handler)((dataMap, command))

  def connectionHandler(connections: Connections, dataMap: DataMap)(
    connection: ZStream.Connection
  ): ZIO[Any, Throwable, Unit] =
    connections.getAndUpdate(_ + 1).flatMap { conn =>
      connection.read
        .splitOnChunk(zio.Chunk.fromIterable("\r\n".getBytes))
        .map(_.map(_.toChar).mkString)
        .via(RESP.commandsScanner)
        .collectType[RESP.SuccessfulCommand]
        .mapZIO(command => RESP.decodeCommand(command).flatMap(commandHandlers(dataMap)))
        .catchSome {
          case UnknownCommand(message)         => ZStream(RESP.Error(message))
          case WrongNumberOfArguments(message) => ZStream(RESP.Error(message))
        }
        .mapZIO(RESP.encodeReply)
        .via(ZPipeline.utf8Encode)
        .run(connection.write)
        .catchSome { case e: IOException => printLine("Boom: " + e.getMessage).unit }
        .ensuring(succeed(println(s"[$conn] Disconnected.")))
        .ensuring(connections.update(_ - 1))
        .unit
    }

  def program =
    for
      port         <- succeed(6666)
      _            <- printLine(("🍤" * 3) + s" dojodis @ port ${port} " + ("🍤" * 3))
      dataMap      <- TMap.empty[String, String].commit
      connections  <- Ref.make[Int](0)
      socketServer <- ZStream
        .fromSocketServer(port)
        .mapZIOParUnordered(4)(connectionHandler(connections, dataMap))
        .runDrain
        .fork
      _            <- socketServer.join
    yield ()

  def run = program
