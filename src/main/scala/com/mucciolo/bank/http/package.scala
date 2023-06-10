package com.mucciolo.bank

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import cats.implicits._
import com.mucciolo.bank.core._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.predicates.all.{Equal, Not}
import eu.timepit.refined.refineV
import io.circe.generic.JsonCodec
import io.circe.refined._

package object http {

  private type NonZero = Not[Equal[0]]

  @JsonCodec(decodeOnly = true)
  final case class AccountBalanceUpdateRequest(amount: BigDecimal Refined NonZero) {
    def toCommand(
      accId: Id, replyTo: ActorRef[StatusReply[AccountEntity.State]]
    ): BankEntity.Command = {
      if (amount > 0)
        BankEntity.Deposit(accId, refineV.unsafeFrom(amount), replyTo)
      else // amount < 0
        BankEntity.Withdraw(accId, refineV.unsafeFrom(-amount), replyTo)
    }
  }

  @JsonCodec(encodeOnly = true)
  final case class BankAccountBalance(balance: BigDecimal)

  @JsonCodec(encodeOnly = true)
  final case class Error(reason: String)

}
