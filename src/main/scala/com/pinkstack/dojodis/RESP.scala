package com.pinkstack.dojodis

import zio.ZIO.{fromEither, succeed}
import zio.stream.{ZPipeline, ZStream}
import zio.{Chunk, UIO, ZIO}

import scala.util.Right

object RESP:
  type Key   = String
  type Value = String

  final case class Command()
  final case class ConfigGet(key: Key)
  final case class Del(key: Key)
  final case class Exists(key: Key)
  final case class Get(key: Key)
  final case class Incr(key: Key)
  final case class IncrBy(key: Key, value: Int)
  final case class Keys(pattern: String)
  final case class Ping()
  final case class Set(key: Key, value: Value)
  final case class MSet(pairs: Seq[(Key, Value)])

  type Commands = Get | Set | MSet | Del | Exists | Incr | IncrBy | Ping | Command | Keys | ConfigGet

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

  final case class UnknownCommand(message: String)         extends Throwable
  final case class WrongNumberOfArguments(message: String) extends Throwable
  type Errors = UnknownCommand | WrongNumberOfArguments

  sealed trait RESPCommand
  case object Empty                                                               extends RESPCommand
  final case class Partial(size: Int = 0, arguments: Array[String] = Array.empty) extends RESPCommand
  final case class SuccessfulCommand(arguments: Array[String])                    extends RESPCommand

  private val decodeRaw: SuccessfulCommand => Either[Errors, Commands] = rawCommand =>
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
      // case Array("mset", pairs*)                                 =>
      //   if (pairs.length % 2 == 0)
      //     val tokens: Seq[(Key, Value)] = pairs.grouped(2).flatten.map { t => (t.he, v) }.toSeq
      //     println(tokens)
      //     Right(MSet.apply(tokens))
      //   else Left(WrongNumberOfArguments("Wrong number of arguments for 'mset' command"))
      case Array(cmd)                                            =>
        Left(UnknownCommand(s"Unsupported command \"${cmd}\""))
      case Array(cmd, args*)                                     =>
        Left(UnknownCommand(s"Unsupported command \"${cmd}\" with arguments ${args.mkString(",")}."))
    }

  val decodeCommand: SuccessfulCommand => ZIO[Any, Throwable, Commands] =
    command => fromEither(decodeRaw(command))

  private val NL                        = "\r\n"
  val encodeReply: Reply => UIO[String] = reply =>
    succeed(
      reply match {
        case _: Ok                   => "+OK"
        case Error(message)          => "-ERR " + message
        case _: Pong                 => "+PONG"
        case ArrayOfStrings(strings) =>
          "*" + strings.length.toString + NL + strings.map(s => "$" + s.length.toString + NL + s).mkString(NL)
        case BulkString(string)      =>
          "$" + string.length.toString + NL + string
        case Nil                     => "$-1"
        case Integer(value)          => s":$value"
      }
    ).map(_ + NL)

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
