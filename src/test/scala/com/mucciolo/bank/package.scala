package com.mucciolo

import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.{Config, ConfigFactory}

package object bank {
  val Config: Config = ConfigFactory.parseString(
    """
      akka.actor.serialization-bindings {
        "com.mucciolo.bank.serialization.CborSerializable" = jackson-cbor
        "scala.math.BigDecimal" = jackson-cbor
      }
      """)
    .withFallback(EventSourcedBehaviorTestKit.config)
}
