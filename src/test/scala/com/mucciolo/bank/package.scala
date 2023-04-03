package com.mucciolo

import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.{Config, ConfigFactory}

package object bank {
  val Config: Config = ConfigFactory.parseString(
    """
      akka.actor.serialization-bindings {
        "com.mucciolo.bank.serialization.CborSerializable" = jackson-cbor
      }
      """)
    .withFallback(EventSourcedBehaviorTestKit.config)
}
