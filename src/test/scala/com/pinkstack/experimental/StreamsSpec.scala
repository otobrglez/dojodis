package com.pinkstack.experimental

import zio.Chunk
import zio.test.*
import zio.ZIO.{fail, succeed}
import zio.test.Annotations.*
import zio.test.Assertion.*
import zio.stream.{UStream, ZStream}

import java.nio.charset.StandardCharsets

sealed trait Command
case object Empty                                                         extends Command
case class Partial(size: Int = 0, arguments: Array[String] = Array.empty) extends Command
case class SuccessfulCommand(arguments: Array[String])                    extends Command

object StreamsSpec extends zio.test.junit.JUnitRunnableSpec:
  def spec = suite("streams basics")(
    test("trying advanced accumulators") {
      val setNameCommand     = "*3\r\n$3\r\nset\r\n$4\r\nname\r\n$3\r\nOto\r\n".getBytes()
      val incrementByCommand = "*3\r\n$6\r\nincrby\r\n$7\r\ncounter\r\n$1\r\n5\r\n".getBytes()
      val getNameCommand     = "*2\r\n$3\r\nget\r\n$4\r\nname\r\n".getBytes()
      val junk               = "junk\r\n".getBytes()

      val incomingStream: ZStream[Any, Nothing, Byte] =
        ZStream.fromIterable(setNameCommand)
          ++ ZStream.fromIterable(setNameCommand)
          ++ ZStream.fromIterable(incrementByCommand)
          ++ ZStream.fromIterable(junk)
          ++ ZStream.fromIterable(incrementByCommand)
          ++ ZStream.fromIterable(getNameCommand)

      val commands = incomingStream
        .splitOnChunk(Chunk.fromIterable("\r\n".getBytes(StandardCharsets.UTF_8)))
        .map(_.map(_.toChar).mkString)
        .scan[Command](Empty) {
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
        .collectType[SuccessfulCommand]
        .tap(o => succeed(println(s"Commands ${o.arguments.mkString(",")}")))
        .runCollect

      assertZIO(commands)(Assertion.isNonEmpty && Assertion.hasSize(Assertion.equalTo(5)))
    }
  )
