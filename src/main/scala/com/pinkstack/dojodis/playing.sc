val raw: Array[String] = """me:first_name "Oto" me:last_name "Brglez"""".split("\\s")

val p = raw
  .grouped(2)
  .map { case Array(x, y) => (x, y) }
  .toList

p.tails
  .filter(_.nonEmpty)
  .flatMap(xs => xs.tail.map((xs.head, _)))
  .toList

p.grouped(2).flatten.map { case (k, v) => (k, v) }.toList
