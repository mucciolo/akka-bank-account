package com.mucciolo.bank

import com.mucciolo.bank.util.Generator
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.atomic.AtomicInteger

final class GeneratorSpec extends AnyWordSpec with Matchers {

  final class SequentialIntGenerator(startingAt: Int) extends Generator[Int] {

    private val NextNumber = new AtomicInteger(startingAt)

    override def next(): Int = NextNumber.getAndIncrement()
  }

  "Generator" when {
    "nextNotIn" should {
      "return a generated element not in the given set" in {
        new SequentialIntGenerator(startingAt = 1).nextNotIn(Set.empty) shouldBe 1
        new SequentialIntGenerator(startingAt = 1).nextNotIn(Set(99)) shouldBe 1
        new SequentialIntGenerator(startingAt = 1).nextNotIn(Set(1)) shouldBe 2
        new SequentialIntGenerator(startingAt = 1).nextNotIn(Set(1, 2)) shouldBe 3
      }
    }
  }

}
