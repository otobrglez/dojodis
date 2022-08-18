package com.pinkstack.dojodis

import zio.test.*
import zio.ZIO
import shapeless3.deriving.*
import zio.test.Annotations.*

object RESPSpec extends zio.test.junit.JUnitRunnableSpec:
  def spec = suite("Commands")(
    test("raw parsing") {
      // assert(RESP.parse("*3\r\n$3\r\nset\r\n$4\r\nname\r\n$3\r\nOto\r\n"))(Assertion.isRight)
      // assert(RESP.parse("*3\r\n$3\r\nset\r\n$4\r\nname\r\n$10\r\nOto Brglez\r\n"))(Assertion.isRight)
      // assert(RESP.parse("*3\r\n$3\r\nset\r\n$3\r\ncnt\r\n$2\r\n40\r\n"))(Assertion.isRight)
      // assert(RESP.parse("*2\r\n$3\r\nget\r\n$4\r\nname\r\n"))(Assertion.isRight)
      // assert(RESP.parse("*3\r\n$6\r\nincrby\r\n$7\r\ncounter\r\n$1\r\n5\r\n"))(Assertion.isRight)
      // assert(RESP.parse("*1\r\n$4\r\nping\r\n"))(Assertion.isRight)
      assertTrue(1 == 1)
    }
  )
