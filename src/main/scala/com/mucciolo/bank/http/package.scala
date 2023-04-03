package com.mucciolo.bank

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import cats.implicits._
import com.mucciolo.bank.core.{AccountEntity, BankEntity}
import com.mucciolo.bank.http.Validation._
import io.circe.generic.JsonCodec

package object http {

  @JsonCodec(decodeOnly = true)
  final case class AccountBalanceUpdateRequest(amount: BigDecimal) {
    def toCommand(
      accId: AccountEntity.Id, replyTo: ActorRef[StatusReply[AccountEntity.State]]
    ): BankEntity.Command = {
      if (amount > 0)
        BankEntity.Deposit(accId, amount, replyTo)
      else if (amount < 0)
        BankEntity.Withdraw(accId, -amount, replyTo)
      else
        throw new IllegalArgumentException("Amount must be different than zero")
    }

  }
  object AccountBalanceUpdateRequest {
    implicit val validator: Validator[AccountBalanceUpdateRequest] = request => {
      validateRequired(request.amount, "amount")
        .andThen(validateNonzero(_, "amount"))
        .map(AccountBalanceUpdateRequest.apply)
    }
  }

  @JsonCodec(encodeOnly = true)
  final case class BankAccountBalance(balance: BigDecimal)

  @JsonCodec(encodeOnly = true)
  final case class Error(reason: String)

}
