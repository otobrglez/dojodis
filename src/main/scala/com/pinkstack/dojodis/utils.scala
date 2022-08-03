package com.pinkstack.dojodis

import zio.ZIO
import zio.ZIO.succeed

object utils:
  extension [R, E, A](zio: ZIO[R, E, A])
    def debugThread: ZIO[R, E, A] =
      zio
        .tap(a => succeed(println(s"[${Thread.currentThread().getName}] - ${a}")))
        .tapErrorCause(cause => succeed(println(s"[${Thread.currentThread().getName}] - ${cause}")))
