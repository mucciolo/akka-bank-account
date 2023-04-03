package com.mucciolo.bank

import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.mucciolo.bank.core.AccountEntity
import com.mucciolo.bank.core.AccountEntity.Error._
import com.mucciolo.bank.core.AccountEntity._

import java.util.UUID

final class AccountEntitySpec extends ActorSpecBase[Command, Event, State] {

  private val accId = UUID.fromString("5d624393-4576-4c45-bcfd-e0e5dc327b40")
  override protected val eventSourcedTestKit: EventSourcedBehaviorTestKit[Command, Event, State] =
    EventSourcedBehaviorTestKit(system, AccountEntity(accId))

  "BankAccount" should {
    "be created with zero balance" in {
      eventSourcedTestKit.getState().balance shouldBe Zero
    }

    "handle deposit of positive values" in {
      val amount = 1.25
      val cmd = Deposit(amount, _)
      val result = eventSourcedTestKit.runCommand(cmd)

      result.reply shouldBe StatusReply.success(State(amount))
      result.event shouldBe Deposited(amount)
      result.state.balance shouldBe amount
    }

    "reject deposit of no value" in {
      val cmd = Deposit(0, _)
      val result = eventSourcedTestKit.runCommand(cmd)

      result.reply shouldBe StatusReply.error(InvalidDeposit)
      result.hasNoEvents shouldBe true
    }

    "reject deposit of non-positive values" in {
      val cmd = Deposit(-1.50, _)
      val result = eventSourcedTestKit.runCommand(cmd)

      result.reply shouldBe StatusReply.error(InvalidDeposit)
      result.hasNoEvents shouldBe true
    }

    "handle withdrawal of positive values" in {
      eventSourcedTestKit.initialize(State(5.0))
      val cmd = Withdraw(2.75, _)
      val result = eventSourcedTestKit.runCommand(cmd)

      result.reply shouldBe StatusReply.success(State(2.25))
      result.event shouldBe Withdrawn(2.75)
      result.state.balance shouldBe 2.25
    }

    "reject withdrawal overdraft" in {
      eventSourcedTestKit.initialize(State(1.11))
      val cmd = Withdraw(2.0, _)
      val result = eventSourcedTestKit.runCommand(cmd)

      result.reply shouldBe StatusReply.error(InsufficientFunds)
      result.hasNoEvents shouldBe true
    }

    "reject withdrawal of no value" in {
      eventSourcedTestKit.initialize(State(2.0))
      val cmd = Withdraw(0.0, _)
      val result = eventSourcedTestKit.runCommand(cmd)

      result.reply shouldBe StatusReply.error(InvalidWithdraw)
      result.hasNoEvents shouldBe true
    }

    "reject withdrawal of non-positive values" in {
      eventSourcedTestKit.initialize(State(3.99))
      val cmd = Withdraw(-2.25, _)
      val result = eventSourcedTestKit.runCommand(cmd)

      result.reply shouldBe StatusReply.error(InvalidWithdraw)
      result.hasNoEvents shouldBe true
    }
  }

}
