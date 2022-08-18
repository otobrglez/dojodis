package com.pinkstack.dojodis

import zio.{Chunk, UIO, ZIO}
import zio.ZIO.succeed
import zio.stream.ZPipeline

import java.nio.charset.StandardCharsets
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
    case ("set", Seq(key, rest*))    =>
      println("##### SET 1")
      println(rest)
      Right(Set(key, rest.head))
    case ("set", Seq(key, rest))     =>
      println(s"##### SET 2 ${rest}")
      Right(Set(key, "what"))
    case ("incr", Seq(key, _))       => Right(Incr(key))
    case ("incrby", Seq(key, rest*)) => Right(IncrBy(key, rest.head))
    case ("command", _)              => Right(Command())
    case (cmd, args)                 =>
      println(args)
      Left(UnknownCommandError(s"Unsupported command \"${cmd}\"."))
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
    println(s"~~~ LINE = ${line}")
    println(s"~~~ LINE = ${line.split("\\r\\n").mkString("#")}")
    // println(s"LINE = ${line.replaceAll("\\r\\n", "__")}")
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
  
  sealed trait RESPCommand
  case object Empty                                                         extends RESPCommand
  case class Partial(size: Int = 0, arguments: Array[String] = Array.empty) extends RESPCommand
  case class SuccessfulCommand(arguments: Array[String])                    extends RESPCommand
  
  
  def commandsParser = ZPipeline
    //.splitOnChunk(Chunk.fromIterable("\r\n".getBytes(StandardCharsets.UTF_8)))
    //.map(_.map(_.toChar).mkString)
    .scan[String, RESPCommand](Empty) {
      case (Empty | _: SuccessfulCommand, chunk) if chunk.startsWith("*") =>
        Partial(size = chunk.substring(1).toInt * 2)
      case (Empty | _: SuccessfulCommand, _) => Empty
      case (command@Partial(size, _), _) if size % 2 == 0 =>
        command.copy(size = size - 1)
      case (command@Partial(size, arguments), chunk) if size - 1 != 0 =>
        command.copy(size = size - 1, arguments = arguments ++ Array(chunk))
      case (Partial(size, arguments), chunk) if size - 1 == 0 =>
        SuccessfulCommand(arguments ++ Array(chunk))
      case _ => Empty
    }
    // .collectType[SuccessfulCommand]

/*
- https://github.com/griggt/dp/blob/44b317a9c352a3097eaa74dc83d2a7e4a4ac462c/tests/run/i10997.scala
- https://scastie.scala-lang.org/1gJL8VuMQG6swqVpdi5Glw
 */
