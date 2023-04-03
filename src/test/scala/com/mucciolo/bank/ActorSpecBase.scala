package com.mucciolo.bank

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

abstract class ActorSpecBase[C, E, S] extends ScalaTestWithActorTestKit(Config)
  with AnyWordSpecLike with BeforeAndAfterEach {

  protected val eventSourcedTestKit: EventSourcedBehaviorTestKit[C, E, S]

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

}
