package com.mucciolo

import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.{Config, ConfigFactory}

package object bank {
  val Config: Config =
    EventSourcedBehaviorTestKit.config
      .withValue(
        "akka.actor.serialization-bindings",
        ConfigFactory.defaultApplication().getValue("akka.actor.serialization-bindings")
      )
}
