package com.pinkstack.dojodis

import zio.*
import zio.ZIO
import zio.Console.printLine

import java.net.{InetAddress, InetSocketAddress, Socket, SocketAddress}
import java.nio.channels.{SelectionKey, Selector, ServerSocketChannel}

object BetterApp extends zio.ZIOAppDefault:

  def server =
    for
      selector <- ZIO.attempt(Selector.open())
      channel  <- ZIO.attempt {
        val serverChannel = ServerSocketChannel.open()
        serverChannel.configureBlocking(false)
        serverChannel.socket().bind(new InetSocketAddress(6666))
        serverChannel.register(selector, SelectionKey.OP_READ)
        serverChannel
      }
      client   <- ZIO.attempt {
        channel.accept()
      }
    yield ()

  def program =
    for
      _ <- printLine("Booting")
      _ <- server
    yield ()

  def run = program
