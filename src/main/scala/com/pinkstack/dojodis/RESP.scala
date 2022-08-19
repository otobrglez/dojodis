package com.pinkstack.dojodis

import zio.ZIO.{fromEither, succeed}
import zio.stream.{ZPipeline, ZStream}
import zio.{Chunk, UIO, ZIO}

import scala.util.Right

object RESP:
  final case class Command()
  final case class ConfigGet(key: String)
  final case class Del(key: String)
  final case class Exists(key: String)
  final case class Get(key: String)
  final case class Incr(key: String)
  final case class IncrBy(key: String, value: Int)
  final case class Keys(pattern: String)
  final case class Ping()
  final case class Set(key: String, value: String)

  type Commands = Get | Set | Del | Exists | Incr | IncrBy | Ping | Command | Keys | ConfigGet

  case object Ok
  type Ok   = Ok.type
  final case class Error(message: String)
  case object Pong
  type Pong = Pong.type
  final case class ArrayOfStrings(strings: Seq[String] = Seq.empty)
  object ArrayOfStrings:
    def of(values: String*) = ArrayOfStrings.apply(strings = values.toSeq)
    val empty               = of()

  final case class BulkString(string: String)
  case object Nil
  type Nil = Nil.type
  final case class Integer(value: Int)

  type Reply = Ok | Error | Pong | ArrayOfStrings | BulkString | Nil | Integer

  final case class UnknownCommandError(message: String) extends Throwable

  sealed trait RESPCommand
  case object Empty                                                               extends RESPCommand
  final case class Partial(size: Int = 0, arguments: Array[String] = Array.empty) extends RESPCommand
  final case class SuccessfulCommand(arguments: Array[String])                    extends RESPCommand

  private val decodeRaw: SuccessfulCommand => Either[UnknownCommandError, Commands] = rawCommand =>
    Array(rawCommand.arguments.head.toLowerCase) ++ rawCommand.arguments.tail match {
      case Array("command", _)                                   => Right(Command())
      case Array("config", get, key) if get.toLowerCase == "get" => Right(ConfigGet(key))
      case Array("del", key)                                     => Right(Del(key))
      case Array("exists", key)                                  => Right(Exists(key))
      case Array("get", key)                                     => Right(Get(key))
      case Array("incr", key)                                    => Right(Incr(key))
      case Array("incrby", key, count)                           => Right(IncrBy(key, count.toInt))
      case Array("keys", pattern)                                => Right(Keys(pattern))
      case Array("ping")                                         => Right(Ping())
      case Array("set", key, value)                              => Right(Set(key, value))
      case Array(cmd)                                            =>
        Left(UnknownCommandError(s"Unsupported command \"${cmd}\""))
      case Array(cmd, args*)                                     =>
        Left(UnknownCommandError(s"Unsupported command \"${cmd}\" with arguments ${args.mkString(",")}."))
    }

  val decodeCommand: SuccessfulCommand => ZIO[Any, Throwable, Commands] =
    command => fromEither(decodeRaw(command))

  val encodeReply: Reply => UIO[String] = reply =>
    for replyPayload <- succeed(
        reply match {
          case _: Ok                   => "+OK" + "\r\n"
          case Error(message)          => "-ERR " + message + "\r\n"
          case _: Pong                 => "+PONG" + "\r\n"
          case ArrayOfStrings(strings) =>
            "*" + strings.length.toString + "\r\n" + strings
              .map(s => "$" + s"${s.length}\r\n${s}")
              .mkString("\r\n") + "\r\n"
          case BulkString(string)      =>
            "$" + string.length.toString + "\r\n" + string + "\r\n"
          case Nil                     => "$-1" + "\r\n"
          case Integer(value)          => s":$value" + "\r\n"
        }
      )
    yield replyPayload

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
