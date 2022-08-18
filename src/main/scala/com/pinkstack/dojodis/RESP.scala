package com.pinkstack.dojodis

import zio.{Chunk, UIO, ZIO}
import zio.ZIO.succeed
import zio.stream.{ZPipeline, ZStream}

import java.nio.charset.StandardCharsets
import scala.compiletime.{constValue, erasedValue, summonInline}
import scala.deriving.*

object RESP:
  case class Get(key: String)
  case class Set(key: String, value: String)
  case class Exists(key: String)
  case class Incr(key: String)
  case class IncrBy(key: String, value: Int)
  case class Ping()
  case class Command()

  type Commands = Get | Set | Exists | Incr | IncrBy | Ping | Command

  case class Ok()
  case class Error(message: String)
  case class Pong()
  case class ArrayOfStrings(strings: Seq[String] = Seq.empty)
  case class BulkString(string: String)
  case object Nil
  type Nil = Nil.type
  case class Integer(value: Int)

  type Reply = Ok | Error | Pong | ArrayOfStrings | BulkString | Nil | Integer

  sealed trait ParsingError                                                                extends Throwable:
    def message: String
  final case class CommandNotArrayError(message: String = "Command is not an RESP array!") extends ParsingError
  final case class ProtocolError(message: String = "Sorry, problem with protocol.")        extends ParsingError
  final case class UnknownCommandError(message: String)                                    extends ParsingError

  sealed trait RESPCommand
  case object Empty                                                         extends RESPCommand
  case class Partial(size: Int = 0, arguments: Array[String] = Array.empty) extends RESPCommand
  case class SuccessfulCommand(arguments: Array[String])                    extends RESPCommand

  def processCommand(cmd: SuccessfulCommand): Either[ParsingError, Commands] =
    cmd.arguments.zipWithIndex.map { case (s, i) =>
      if (i == 0) s.toLowerCase() else s
    } match {
      case Array("get", key)           => Right(Get(key))
      case Array("ping")               => Right(Ping())
      case Array("exists", key)        => Right(Exists(key))
      case Array("set", key, value)    => Right(Set(key, value))
      case Array("incr", key)          => Right(Incr(key))
      case Array("incrby", key, count) => Right(IncrBy(key, count.toInt))
      case Array("command", _)         => Right(Command())
      case Array(cmd)                  =>
        Left(UnknownCommandError(s"Unsupported command \"${cmd}\""))
      case Array(cmd, args*)           =>
        Left(UnknownCommandError(s"Unsupported command \"${cmd}\" with arguments ${args.mkString(",")}."))
    }

  def encodeReply(reply: Reply): UIO[String] = succeed {
    reply match {
      case _: Ok                   => "+OK" + "\r\n"
      case Error(message)          => "-ERR " + message + "\r\n"
      case _: Pong                 => "+PONG" + "\r\n"
      case ArrayOfStrings(strings) =>
        "*" + strings.length.toString + "\r\n" + strings.mkString("\r\n")
      case BulkString(string)      =>
        "$" + string.length.toString + "\r\n" + string + "\r\n"
      case Nil                     =>
        "$-1\r\n"
      case Integer(value)          =>
        s":${value}\r\n"
    }
  }

  def commandsScanner: ZPipeline[Any, Throwable, String, RESPCommand] = ZPipeline
    .scan[String, RESPCommand](Empty) {
      case (Empty | _: SuccessfulCommand, chunk) if chunk.startsWith("*") =>
        Partial(size = chunk.substring(1).toInt * 2)
      case (Empty | _: SuccessfulCommand, _)                              => Empty
      case (command @ Partial(size, _), _) if size % 2 == 0 =>
        command.copy(size = size - 1)
      case (command @ Partial(size, arguments), chunk) if size - 1 != 0 =>
        command.copy(size = size - 1, arguments = arguments ++ Array(chunk))
      case (Partial(size, arguments), chunk) if size - 1 == 0           =>
        SuccessfulCommand(arguments ++ Array(chunk))
      case _                                                            => Empty
    }
