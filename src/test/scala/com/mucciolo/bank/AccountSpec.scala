package com.mucciolo.bank

import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.mucciolo.bank.core.Account._
import com.mucciolo.bank.core.{Account, PositiveAmount}
import eu.timepit.refined.refineMV

import java.util.UUID
import scala.language.implicitConversions

final class AccountSpec extends ActorSpecBase[Action, Event, State] {

  private val accId = UUID.fromString("5d624393-4576-4c45-bcfd-e0e5dc327b40")
  override protected val eventSourcedTestKit: EventSourcedBehaviorTestKit[Action, Event, State] =
    EventSourcedBehaviorTestKit(system, Account(accId))

  implicit def doubleToPositiveAmount(double: Double): BigDecimal = BigDecimal(double)

  "Account" should {
    "be created with zero balance" in {
      eventSourcedTestKit.getState().balance shouldBe Zero
    }

    "handle deposit" in {
      val transactionId = UUID.fromString("ecfeff75-d4b6-47ce-8670-90ad088bacc0")
      val amount: PositiveAmount = refineMV(BigDecimal(1.25))
      val deposit = Deposit(transactionId, amount, _)
      val result = eventSourcedTestKit.runCommand(deposit)

      result.event should matchPattern { case Deposited(_, `amount`) => }
      result.state shouldBe State(amount.value)
      result.reply shouldBe DepositSuccess(transactionId, 1.25)
    }

    "handle withdrawal" in {
      val initialState = State(5.0)
      eventSourcedTestKit.initialize(initialState)

      val transactionId = UUID.fromString("6a76270c-d62d-4977-b355-20d8b949a457")
      val amount: PositiveAmount = refineMV(BigDecimal(2.75))
      val withdraw = Withdraw(transactionId, amount, _)
      val result = eventSourcedTestKit.runCommand(withdraw)

      result.event should matchPattern { case Withdrawn(_, `amount`) => }
      result.state shouldBe State(initialState.balance - amount.value)
      result.reply shouldBe WithdrawSuccess(transactionId, 2.25)
    }

    "reject withdrawal overdraft" in {
      eventSourcedTestKit.initialize(State(1.11))

      val transactionId = UUID.fromString("4afdbc17-8811-47cf-84d6-9ecdae78610b")
      val amount: PositiveAmount = refineMV(BigDecimal(2.0))
      val withdraw = Withdraw(transactionId, amount, _)
      val result = eventSourcedTestKit.runCommand(withdraw)

      result.reply shouldBe InsufficientFunds(transactionId)
      result.hasNoEvents shouldBe true
    }

    "handle balance request" in {
      val state = State(5)
      eventSourcedTestKit.initialize(state)

      val queryId = UUID.fromString("4ff9e366-852b-46fe-a424-1a0d38c31a41")
      val getBalance = GetBalance(queryId, _)
      val result = eventSourcedTestKit.runCommand(getBalance)

      result.reply shouldBe GetBalanceResponse(queryId, 5)
      result.hasNoEvents shouldBe true
    }
  }
}
