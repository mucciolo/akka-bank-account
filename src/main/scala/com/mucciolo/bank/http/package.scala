package com.mucciolo.bank

import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.predicates.all.{Equal, Not}
import io.circe.generic.JsonCodec
import io.circe.refined._

package object http {

  private type NonZero = Not[Equal[0]]
  type NonZeroBigDecimal = BigDecimal Refined NonZero

  @JsonCodec(decodeOnly = true)
  final case class AccountBalanceUpdateRequest(amount:NonZeroBigDecimal)

  @JsonCodec(encodeOnly = true)
  final case class BankAccountBalance(balance: BigDecimal)

  @JsonCodec(encodeOnly = true)
  final case class Error(reason: String)

}
