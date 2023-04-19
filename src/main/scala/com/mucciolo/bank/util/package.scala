package com.mucciolo.bank

package object util {
  trait Generator[T] {

    private val iterator: Iterator[T] = Iterator.continually(next())

    def next(): T

    /**
      * @note can halt the calling thread
      */
    final def nextNotIn(set: Set[T]): T = {
      iterator.filterNot(set.contains).next()
    }
  }
}
