package com.pinkstack.dojodis

import zio.{UIO, ZIO}
import zio.ZIO.succeed

import scala.compiletime.{constValue, erasedValue, summonInline}
import scala.deriving.*

object RESP:
  case class Get(key: String)
  case class Set(key: String, value: String)
  case class Exists(key: String)
  case class Incr(key: String)
  case class IncrBy(key: String, value: String)
  case class Ping()
  case class Command()

  type Commands = Get | Set | Exists | Incr | IncrBy | Ping | Command

  case class Ok()
  case class Error(message: String)
  case class Pong()
  case class ArrayOfStrings(strings: Seq[String] = Seq.empty)

  type Reply = Ok | Error | Pong | ArrayOfStrings

  enum FirstByte(val string: String):
    case SimpleString extends FirstByte("+")
    case Error        extends FirstByte("-")
    case Integer      extends FirstByte(":")
    case BulkString   extends FirstByte("$")
    case Array        extends FirstByte("*")

  sealed trait ParsingError                                                                extends Throwable:
    def message: String
  final case class CommandNotArrayError(message: String = "Command is not an RESP array!") extends ParsingError
  final case class ProtocolError(message: String = "Sorry, problem with protocol.")        extends ParsingError
  final case class UnknownCommandError(message: String)                                    extends ParsingError

  type CommandStruct = (String, Seq[String])

  val processCommand: CommandStruct => Either[ParsingError, Commands] = {
    case ("get", Seq(key))           => Right(Get(key))
    case ("ping", _)                 => Right(Ping())
    case ("exists", Seq(key, _))     => Right(Exists(key))
    case ("set", Seq(key, rest*))    => Right(Set(key, rest.head))
    case ("incr", Seq(key, _))       => Right(Incr(key))
    case ("incrby", Seq(key, rest*)) => Right(IncrBy(key, rest.head))
    case ("command", _)              => Right(Command())
    case (cmd, _)                    => Left(UnknownCommandError(s"Unsupported command \"${cmd}\"."))
  }

  def filterParams: CommandStruct => CommandStruct = (command, params) =>
    (command.toLowerCase(), params.zipWithIndex.filter(_._2 % 2 == 1).map(_._1))

  val mapCommand: CommandStruct => Either[ParsingError, Commands] =
    filterParams andThen processCommand

  private val processCommandArray: String => Either[ParsingError, Commands] = raw =>
    raw.split("\\r\\n").toList match {
      case List(isArray, _*) if !isArray.startsWith(FirstByte.Array.string) => Left(CommandNotArrayError())
      case List(_, _, command: String, rest*)                               => mapCommand(command, rest)
      case _                                                                => Left(ProtocolError())
    }

  val parse: String => Either[ParsingError, Commands] = processCommandArray

  def encodeCommand(line: String): ZIO[Any, ParsingError, Commands] =
    ZIO.fromEither(parse(line))

  def decodeReply(reply: Reply): UIO[String] = succeed {
    reply match {
      case _: Ok                   => FirstByte.SimpleString.string + "OK" + "\r\n"
      case Error(message)          => FirstByte.Error.string + "ERR " + message + "\r\n"
      case _: Pong                 =>
        FirstByte.SimpleString.string + "PONG" + "\r\n"
      case ArrayOfStrings(strings) =>
        FirstByte.Array.string + strings.length.toString + "\r\n" + strings.mkString("\r\n")
    }
  }

/*
- https://github.com/griggt/dp/blob/44b317a9c352a3097eaa74dc83d2a7e4a4ac462c/tests/run/i10997.scala
- https://scastie.scala-lang.org/1gJL8VuMQG6swqVpdi5Glw
 */
