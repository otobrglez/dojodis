# dojodis 🍤

[dojodis] is an experimental clone of [Redis][redis] in [Scala ZIO][zio].

## Development

```bash
PORT=8888 sbt run

redis-cli -p 8888 set my_name "Oto Brglez"
redis-cli -p 8888 get my_name
```

## Resources

- [RESP protocol spec](https://redis.io/docs/reference/protocol-spec/)
- [A Java Parallel Server Through the Ages](https://www.cs.unh.edu/~charpov/programming-futures.html)
- [Wrapping impure code with ZIO - Pierre Ricadat](https://medium.com/@ghostdogpr/wrapping-impure-code-with-zio-9265c219e2e)
- [zio-tcp - Richard Searle](https://github.com/searler/zio-tcp)
- [5 lessons learned from my continuing awesome journey with ZIO - Natan Silnitsky](https://medium.com/wix-engineering/5-lessons-learned-from-my-continuing-awesome-journey-with-zio-66319d12ed7c)
- [Thread Pool Best Practices with ZIO - John A De Goes](https://degoes.net/articles/zio-threads)
- [Detecting Client Disconnections Using Java Sockets - Zack West](https://www.alpharithms.com/detecting-client-disconnections-java-sockets-091416/)
- [ZIO / ZIO](https://github.com/zio/zio/issues/3649#issuecomment-631541249)
- [How to talk raw Redis](https://www.compose.com/articles/how-to-talk-raw-redis/)
- [Processing ZIO effects through a pipeline - Steven Vroonland](https://medium.com/@svroonland/processing-zio-effects-through-a-pipeline-c469e28dff62)
- [High-Concurrency Practices of Redis: Snap-Up System](https://www.alibabacloud.com/blog/high-concurrency-practices-of-redis-snap-up-system_597858)
- [kpodsiad / yamlator](https://github.com/kpodsiad/yamlator)
- [Inline your boilerplate – harnessing Scala 3 metaprogramming without macros](https://scalac.io/blog/inline-your-boilerplate-harnessing-scala3-metaprogramming-without-macros/)
- [From Scala 2 shapeless to Scala 3](http://www.limansky.me/posts/2021-07-26-from-scala-2-shapeless-to-scala-3.html)


## Authors

- [Oto Brglez / @otobrglez][otobrglez]

![Twitter Follow](https://img.shields.io/twitter/follow/otobrglez?style=social)

[dojodis]: https://github.com/otobrglez/dojodis

[redis]: https://redis.io

[zio]: https://zio.dev

[otobrglez]: https://github.com/otobrglez
