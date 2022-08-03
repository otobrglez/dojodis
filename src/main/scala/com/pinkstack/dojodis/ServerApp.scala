package com.pinkstack.dojodis

import zio.*
import ZIO.{
  acquireRelease,
  acquireReleaseExit,
  attempt,
  attemptBlocking,
  attemptBlockingIOUnsafe,
  fail,
  failCause,
  scoped,
  succeed
}
import Console.printLine

import java.io.{IOException, PrintWriter}
import java.net.{InetAddress, InetSocketAddress, ServerSocket, Socket, SocketException}
import java.util.concurrent.atomic.AtomicBoolean
import utils.*

object ServerApp extends zio.ZIOAppDefault:
  def accept(server: ServerSocket): ZIO[zio.Scope, Throwable, Unit] = ZIO.scoped {
    for
      socket  <- acquireRelease(attemptBlocking(server.accept()))(s => succeed(s.close()) *> succeed(println("Closed")))
      address <- succeed(socket.getRemoteSocketAddress)
      _       <- ZIO.succeed(println(s"[${address}] Client successfully connected."))
      rw = ZIO.attempt {
        println(s"[${address}] [${Thread.currentThread().getName}] Doing something.")

        val out = new PrintWriter(socket.getOutputStream, true)
        out.println("+PONG")

      }

      clientConnected = attemptBlocking(socket.getInputStream.read() == -1) *> succeed(socket.close()) *> succeed(
        println(s"[${address}] Auto closed.")
      )
      loop <- (clientConnected <&> rw).fork *> accept(server) *> ZIO.unit
    yield loop
  }

  def server: ZIO[Any, Throwable, Unit] = ZIO.scoped {
    for
      serverSocket <- acquireRelease(
        attempt(new ServerSocket(6666, 5, InetAddress.getByName("0.0.0.0")))
      )(socket => succeed(socket.close()))
      fib          <- accept(serverSocket).fork
      _            <- fib.await
    yield ()
  }

  def program =
    for
      _ <- printLine("Booting,...")
      _ <- server
    yield ()

  def run = program
