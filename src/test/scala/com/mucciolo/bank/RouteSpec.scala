package com.mucciolo.bank

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

trait RouteSpec extends AnyWordSpec with Matchers with ScalatestRouteTest
  with OptionValues with MockFactory
