package com.mucciolo.bank

import cats.implicits._
import com.mucciolo.bank.core.{AccountStatement, AccountStatementEntry}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.predicates.all.{Equal, Not}
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.deriveEncoder
import io.circe.refined._
import io.circe.{Encoder, JsonNumber}

import scala.math.BigDecimal.RoundingMode.UNNECESSARY

package object http {

  private type NonZero = Not[Equal[0]]
  type NonZeroBigDecimal = BigDecimal Refined NonZero

  @JsonCodec(decodeOnly = true)
  final case class AccountBalanceUpdateRequest(amount: NonZeroBigDecimal)

  @JsonCodec(encodeOnly = true)
  final case class BankAccountBalance(balance: BigDecimal)

  @JsonCodec(encodeOnly = true)
  final case class Error(reason: String)

  object codecs {
    // encoder for BigDecimal with two decimal places
    implicit val bigDecimalEncoder: Encoder[BigDecimal] = Encoder.encodeJsonNumber.contramap {
      bigDecimal => JsonNumber.fromDecimalStringUnsafe(bigDecimal.setScale(2, UNNECESSARY).toString())
    }
    implicit val accountStatementEntryEncoder: Encoder[AccountStatementEntry] = deriveEncoder
    implicit val accountStatementEncoder: Encoder[AccountStatement] = deriveEncoder
  }

}
