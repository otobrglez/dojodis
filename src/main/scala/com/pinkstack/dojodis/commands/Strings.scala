package com.pinkstack.dojodis.commands

import com.pinkstack.dojodis.DataMap
import com.pinkstack.dojodis.RESP.*
import zio.ZIO
import ZIO.{fail, succeed}

object Strings:
  def incrementBy(dataMap: DataMap, key: String, n: Int = 1) =
    for update <- (for
        current <- dataMap.getOrElse(key, "0")
        newValue = current.toInt + n
        _ <- dataMap.put(key, newValue.toString)
      yield newValue).commit
    yield Integer(update)

  val handler: PartialFunction[(DataMap, Commands), ZIO[Any, Throwable, Reply]] =
    case (dataMap, Get(key))           =>
      dataMap.get(key).commit.map {
        case Some(value) => BulkString(value)
        case None        => Nil
      }
    case (dataMap, Set(key, value))    => dataMap.put(key, value).commit.as(Ok)
    case (dataMap, Del(key))           => dataMap.delete(key).commit.as(Integer(1))
    case (dataMap, Exists(key))        =>
      dataMap.contains(key).commit.map {
        case true  => Integer(1)
        case false => Integer(0)
      }
    case (dataMap, Incr(key))          => incrementBy(dataMap, key)
    case (dataMap, IncrBy(key, value)) => incrementBy(dataMap, key, value)
    case (dataMap, Keys(pattern))      =>
      dataMap.keys.commit
        .map(_.filter(_.startsWith(pattern)))
        .map(ArrayOfStrings.apply)

object Hacks:
  val handler: PartialFunction[(DataMap, Commands), ZIO[Any, Throwable, Reply]] =
    case (_, _: Command)                            => succeed(Nil)
    case (_, ConfigGet(key)) if key == "save"       => succeed(ArrayOfStrings.of("save", "3600 1 300 100 60 10000"))
    case (_, ConfigGet(key)) if key == "appendonly" => succeed(ArrayOfStrings.of("appendonly", "no"))
    case (_, _: ConfigGet)                          => succeed(ArrayOfStrings.empty)
