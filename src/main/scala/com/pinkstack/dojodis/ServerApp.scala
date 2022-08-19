/*
 * Copyright 2022 Oto Brglez - <otobrglez@gmail.com>
 */

package com.pinkstack.dojodis

import com.pinkstack.dojodis.RESP.*
import zio.{Ref, ZIO}
import zio.Console.printLine
import zio.ZIO.succeed
import zio.stm.TMap
import zio.stream.{Stream, ZPipeline, ZStream}

import java.io.IOException

object ServerApp extends zio.ZIOAppDefault:
  type DataMap     = TMap[String, String]
  type Connections = Ref[Int]

  def commandHandler(dataMap: DataMap)(command: Commands): ZIO[Any, Throwable, Reply] =
    def incrementBy(key: String, n: Int = 1) =
      for update <- (for
          current <- dataMap.getOrElse(key, "0")
          newValue = current.toInt + n
          _ <- dataMap.put(key, newValue.toString)
        yield newValue).commit
      yield Integer(update)

    command match
      case Get(key)                              =>
        dataMap.get(key).commit.map {
          case Some(value) => BulkString(value)
          case None        => Nil
        }
      case Set(key, value)                       => dataMap.put(key, value).commit.as(Ok)
      case Del(key)                              => dataMap.delete(key).commit.as(Integer(1))
      case Exists(key)                           =>
        dataMap.contains(key).commit.map {
          case true  => Integer(1)
          case false => Integer(0)
        }
      case Incr(key)                             => incrementBy(key)
      case IncrBy(key, value)                    => incrementBy(key, value)
      case Keys(pattern)                         =>
        dataMap.keys.commit
          .map(_.filter(_.startsWith(pattern)))
          .map(ArrayOfStrings.apply)
      case _: Ping                               => succeed(Pong)
      case _: Command                            => succeed(Nil)
      case ConfigGet(key) if key == "save"       => succeed(ArrayOfStrings.of("save", "3600 1 300 100 60 10000"))
      case ConfigGet(key) if key == "appendonly" => succeed(ArrayOfStrings.of("appendonly", "no"))
      case _: ConfigGet                          => succeed(ArrayOfStrings.empty)

  def connectionHandler(connections: Connections, dataMap: DataMap)(
    connection: ZStream.Connection
  ): ZIO[Any, Throwable, Unit] =
    connections.getAndUpdate(_ + 1).flatMap { conn =>
      connection.read
        .splitOnChunk(zio.Chunk.fromIterable("\r\n".getBytes))
        .map(_.map(_.toChar).mkString)
        .via(RESP.commandsScanner)
        .collectType[RESP.SuccessfulCommand]
        .mapZIO(command => RESP.decodeCommand(command).flatMap(commandHandler(dataMap)))
        .catchSome { case UnknownCommandError(message) => ZStream(RESP.Error(message)) }
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
