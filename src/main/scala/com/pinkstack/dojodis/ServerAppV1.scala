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

object ServerAppV1 extends zio.ZIOAppDefault:
  def accept(server: ServerSocket, connections: Ref[Int]): ZIO[zio.Scope, Throwable, Unit] = ZIO.scoped {
    for
      socket  <- acquireRelease(attemptBlocking(server.accept()))(s => succeed(s.close()) *> succeed(println("Closed")))
      address <- succeed(socket.getRemoteSocketAddress)
      count   <- connections.updateAndGet(_ + 1)
      _       <- ZIO.succeed(
        println(s"[${address}] [${Thread.currentThread().getName}] Client successfully connected. ${count}")
      )

      rw = ZIO.attempt {
        println(s"[${address}] [${Thread.currentThread().getName}] Doing something.")

        val out = new PrintWriter(socket.getOutputStream, true)
        out.println("+PONG")

      }

      clientConnected = attemptBlocking(socket.getInputStream.read() == -1) *> succeed(socket.close()) *> connections
        .update(_ - 1) *> succeed(
        println(s"[${address}] Auto closed.")
      )
      loop <- (clientConnected <&> rw).fork *> accept(server, connections) *> ZIO.unit
    yield loop
  }

  def server: ZIO[Any, Throwable, Unit] = ZIO.scoped {
    for
      connections  <- Ref.make[Int](0)
      serverSocket <- acquireRelease(
        attempt(new ServerSocket(6666, 5, InetAddress.getByName("0.0.0.0")))
      )(socket => succeed(socket.close()))
      fib          <- accept(serverSocket, connections).fork
      _            <- fib.await
    yield ()
  }

  def program =
    for
      _ <- printLine("Booting,...")
      _ <- server
    yield ()

  def run = program
