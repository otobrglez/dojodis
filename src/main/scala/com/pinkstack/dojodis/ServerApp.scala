package com.pinkstack.dojodis

import com.pinkstack.dojodis.RESP.*
import com.pinkstack.dojodis.utils.*
import zio.*
import zio.Console.printLine
import zio.ZIO.{attempt, attemptBlocking, fail, fromEither, succeed}
import zio.stm.TMap
import zio.stream.{Stream, ZPipeline, ZStream}

import java.io.{IOException, PrintWriter}
import java.net.{InetAddress, ServerSocket, Socket as ClientSocket}

object ServerApp extends zio.ZIOAppDefault:
  type DataMap = TMap[String, String]

  def commandHandler(dataMap: DataMap)(command: Commands): ZIO[Any, Throwable, Reply] =
    command match
      case Get(key)           =>
        dataMap.get(key).commit.map {
          case Some(value) => BulkString(value)
          case None        => Nil
        }
      case Set(key, value)    => dataMap.put(key, value).commit.as(Ok())
      case Exists(key)        =>
        dataMap.contains(key).commit.map {
          case true  => Integer(1)
          case false => Integer(0)
        }
      case Incr(key)          =>
        {
          for
            current <- dataMap.getOrElse(key, "0")
            newValue = current.toInt + 1
            _ <- dataMap.put(key, newValue.toString)
          yield newValue
        }.commit.map(Integer.apply)
      case IncrBy(key, value) =>
        {
          for
            current <- dataMap.getOrElse(key, "0")
            newValue = current.toInt + value
            _ <- dataMap.put(key, newValue.toString)
          yield newValue
        }.commit.map(Integer.apply)
      case ping: Ping         => succeed(Pong())
      case command: Command   => succeed(ArrayOfStrings())

  def connectionHandler(connections: Ref[Int], dataMap: DataMap)(
    connection: ZStream.Connection
  ): ZIO[Any, Throwable, Unit] =
    connection.read
      .splitOnChunk(zio.Chunk.fromIterable("\r\n".getBytes))
      .map(_.map(_.toChar).mkString)
      .via(RESP.commandsScanner)
      .collectType[RESP.SuccessfulCommand]
      .mapZIO(command => fromEither(RESP.processCommand(command)))
      .mapZIO(commandHandler(dataMap))
      .catchSome { case e: ParsingError =>
        ZStream(RESP.Error(e.message))
      }
      .tap(c => succeed(println(s"Out ${c}")))
      .mapZIO(RESP.encodeReply)
      .via(ZPipeline.utf8Encode)
      .run(connection.write)
      .catchSome { case e: IOException =>
        printLine(e.getMessage).unit
      }
      .ensuring(connections.update(_ - 1))
      .unit
      .forever

  def program =
    for
      port         <- succeed(6666)
      _            <- printLine(("🍤" * 3) + s" dojodis @ port ${port} " + ("🍤" * 3))
      connections  <- Ref.make(0)
      dataMap      <- TMap.empty[String, String].commit
      socketServer <- ZStream
        .fromSocketServer(port)
        .mapZIOParUnordered(10)(connectionHandler(connections, dataMap))
        .runDrain
        .fork
      _            <- socketServer.join
    yield ()

  def run = program
