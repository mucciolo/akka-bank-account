package com.mucciolo.bank

import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.mucciolo.bank.core.{AccountEntity, PositiveAmount}
import com.mucciolo.bank.core.AccountEntity.Error._
import com.mucciolo.bank.core.AccountEntity._
import eu.timepit.refined.refineMV

import java.util.UUID
import scala.language.implicitConversions

final class AccountEntitySpec extends ActorSpecBase[Command, Event, State] {

  private val accId = UUID.fromString("5d624393-4576-4c45-bcfd-e0e5dc327b40")
  override protected val eventSourcedTestKit: EventSourcedBehaviorTestKit[Command, Event, State] =
    EventSourcedBehaviorTestKit(system, AccountEntity(accId))

  implicit def doubleToPositiveAmount(double: Double): BigDecimal = BigDecimal(double)

  "BankAccount" should {
    "be created with zero balance" in {
      eventSourcedTestKit.getState().balance shouldBe Zero
    }

    "handle deposit" in {
      val amount: PositiveAmount = refineMV(BigDecimal(1.25))
      val cmd = Deposit(amount, _)
      val result = eventSourcedTestKit.runCommand(cmd)
      val expectedState = State(amount.value)

      result.event shouldBe Deposited(amount)
      result.state shouldBe expectedState
      result.reply shouldBe StatusReply.success(expectedState)
    }

    "handle withdrawal" in {
      val initialState = State(5.0)
      eventSourcedTestKit.initialize(initialState)

      val amount: PositiveAmount = refineMV(BigDecimal(2.75))
      val cmd = Withdraw(amount, _)
      val result = eventSourcedTestKit.runCommand(cmd)
      val expectedState = State(initialState.balance - amount.value)

      result.event shouldBe Withdrawn(amount)
      result.state shouldBe expectedState
      result.reply shouldBe StatusReply.success(expectedState)
    }

    "reject withdrawal overdraft" in {
      eventSourcedTestKit.initialize(State(1.11))

      val amount: PositiveAmount = refineMV(BigDecimal(2.0))
      val cmd = Withdraw(amount, _)
      val result = eventSourcedTestKit.runCommand(cmd)

      result.reply shouldBe StatusReply.error(InsufficientFunds)
      result.hasNoEvents shouldBe true
    }
  }
}
