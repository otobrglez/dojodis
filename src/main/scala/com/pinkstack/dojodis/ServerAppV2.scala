package com.pinkstack.dojodis

import zio.*
import zio.ZIO.{attempt, attemptBlocking, fail, succeed}
import zio.Console.printLine

import java.io.{IOException, PrintWriter}
import java.net.{InetAddress, ServerSocket, Socket as ClientSocket}
import zio.stream.{Stream, ZStream}

object ServerAppV2 extends zio.ZIOAppDefault:

  def mkServerSocket(port: Int, address: String = "127.0.0.1"): ZIO[zio.Scope, IOException, ServerSocket] =
    ZIO.acquireRelease(
      attempt(new ServerSocket(port, 50, InetAddress.getByName(address))).refineToOrDie[IOException]
    )(s => succeed(s.close()))

  def connectionLoop(
    serverSocket: ServerSocket,
    connections: Ref[Int]
  )(worker: ClientSocket => ZIO[Any, Throwable, Unit]): ZIO[Any, Throwable, Unit] =
    for
      socket    <- attemptBlocking(serverSocket.accept())
      workerFib <- connections.update(_ + 1) *> worker(socket).fork
      loop      <- connectionLoop(serverSocket, connections)(worker)
      _         <- workerFib.await
    yield loop

  def server =
    for
      connections  <- Ref.make(0)
      serverSocket <- mkServerSocket(6666)
      _            <- connectionLoop(serverSocket, connections) { socket =>
        {
          for
            connectionsCount <- connections.get
            out              <- succeed(new PrintWriter(socket.getOutputStream, true))
            _                <- printLine(s"Connections: ${connectionsCount}")
            _                <- {
              for
                currentTime <- Clock.currentDateTime
                _           <- succeed(out.println(s"Hello at ${currentTime}"))
              yield ()
            }.repeat(Schedule.spaced(1.second))
          yield ()
        }.ensuring(succeed(socket.close()) *> connections.update(_ - 1))
      }
    yield ()

  def program =
    for
      _   <- printLine("Booting BetterApp")
      fib <- server.fork
      _   <- fib.join
    yield ()

  def run = program
