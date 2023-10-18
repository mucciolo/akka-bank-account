package com.mucciolo.bank

import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.CommandResultWithReply
import akka.util.Timeout
import com.mucciolo.bank.core.Bank._
import com.mucciolo.bank.core.{Account, Bank, Id, PositiveAmount}
import eu.timepit.refined.refineMV

import java.util.UUID
import scala.concurrent.duration.DurationInt

final class BankSpec extends ActorSpecBase[Action, Event, State] {

  private val BankId = UUID.fromString("5d624393-4576-4c45-bcfd-e0e5dc327b40")
  private val BankTimeout: Timeout = Timeout(10.millis)
  override protected val eventSourcedTestKit: EventSourcedBehaviorTestKit[Action, Event, State] =
    EventSourcedBehaviorTestKit(system, Bank(BankId)(BankTimeout))

  "Bank" should {
    "create one account" in {
      val createAccount = CreateAccount(_)
      val result = eventSourcedTestKit.runCommand(createAccount)
      val accId = extractAccId(result)

      result.reply shouldBe CreateAccountResponse(accId)
      result.state.accountById.keySet should contain only accId
    }

    "create two accounts" in {
      val firstAccId = UUID.fromString("3f86b1ef-c547-40a5-80ed-fd5d4945d7e4")
      val firstAcc = spawn(Account(firstAccId), "first-account")
      val initialState = State(Map(firstAccId -> firstAcc.ref))
      eventSourcedTestKit.initialize(initialState)

      val createAccount = CreateAccount(_)
      val result = eventSourcedTestKit.runCommand(createAccount)
      val secondAccId = extractAccId(result)

      result.reply shouldBe CreateAccountResponse(secondAccId)
      result.state.accountById.keySet should contain only (firstAccId, secondAccId)
    }

    "handle deposit" in {
      val accId = UUID.fromString("8a1d1c06-140d-4660-81dd-305fe8d7590e")
      val acc = spawn(Account(accId))
      val initialState = State(Map(accId -> acc.ref))
      eventSourcedTestKit.initialize(initialState)

      val amount: PositiveAmount = refineMV(BigDecimal(10.55))
      val deposit = Deposit(accId, amount, _)
      val result = eventSourcedTestKit.runCommand(deposit)

      result.reply shouldBe DepositSuccess(10.55)
      result.hasNoEvents shouldBe true
    }

    "reject deposit to non-existent account" in {
      val accId = UUID.fromString("1ed7affd-d8e5-4849-b6f4-de99db0dabf8")
      val acc = spawn(Account(accId))
      val initialState = State(Map(accId -> acc.ref))
      eventSourcedTestKit.initialize(initialState)

      val nonexistentAccId = UUID.fromString("9fa33c2c-5720-49e6-9946-ce90763d9f33")
      val amount: PositiveAmount = refineMV(BigDecimal(2.34))
      val deposit = Deposit(nonexistentAccId, amount, _)
      val result = eventSourcedTestKit.runCommand(deposit)

      result.reply shouldBe DepositAccountNotFound
      result.hasNoEvents shouldBe true
    }

    "handle deposit timeout" in {
      val accId = UUID.fromString("1dc0c131-09b0-4e9c-afad-0ad4efe85f80")
      val acc = createTestProbe[Account.Action]()
      val initialState = State(Map(accId -> acc.ref))
      eventSourcedTestKit.initialize(initialState)

      val amount: PositiveAmount = refineMV(BigDecimal(99.57))
      val deposit = Deposit(accId, amount, _)
      val result = eventSourcedTestKit.runCommand(deposit)

      result.reply shouldBe a[DepositFailure]
      result.hasNoEvents shouldBe true
    }

    "handle withdraw" in {
      val accId = UUID.fromString("8781d33e-f2f0-4ba4-af18-1b492055826a")
      val acc = spawn(Account(accId, Account.State(6.00)))
      val initialState = State(Map(accId -> acc.ref))
      eventSourcedTestKit.initialize(initialState)

      val amount: PositiveAmount = refineMV(BigDecimal(5.07))
      val withdraw = Withdraw(accId, amount, _)
      val result = eventSourcedTestKit.runCommand(withdraw)

      result.reply shouldBe WithdrawSuccess(0.93)
      result.hasNoEvents shouldBe true
    }

    "reject withdraw on insufficient funds" in {
      val accId = UUID.fromString("80077239-a433-41c9-a5ad-cae76819ad04")
      val acc = spawn(Account(accId, Account.State(50.00)))
      val initialState = State(Map(accId -> acc.ref))
      eventSourcedTestKit.initialize(initialState)

      val amount: PositiveAmount = refineMV(BigDecimal(100.00))
      val withdraw = Withdraw(accId, amount, _)
      val result = eventSourcedTestKit.runCommand(withdraw)

      result.reply shouldBe WithdrawInsufficientFunds
      result.hasNoEvents shouldBe true
    }

    "reject withdraw of non-existent account" in {
      val accId = UUID.fromString("b36aa1b2-3f9b-4258-a51b-69127d6fd5fd")
      val acc = createTestProbe[Account.Action]()
      val initialState = State(Map(accId -> acc.ref))
      eventSourcedTestKit.initialize(initialState)

      val nonexistentAccId = UUID.fromString("0572cd52-802d-4982-b3f3-4137c5997840")
      val amount: PositiveAmount = refineMV(BigDecimal(350.00))
      val withdraw = Withdraw(nonexistentAccId, amount, _)
      val result = eventSourcedTestKit.runCommand(withdraw)

      result.reply shouldBe WithdrawAccountNotFound
      result.hasNoEvents shouldBe true
    }

    "handle withdraw timeout" in {
      val accId = UUID.fromString("8a1dcf1e-8b11-455a-9526-c35cc24eab0d")
      val acc = createTestProbe[Account.Action]()
      val initialState = State(Map(accId -> acc.ref))
      eventSourcedTestKit.initialize(initialState)

      val amount: PositiveAmount = refineMV(BigDecimal(150.00))
      val withdraw = Withdraw(accId, amount, _)
      val result = eventSourcedTestKit.runCommand(withdraw)

      result.reply shouldBe a[WithdrawFailure]
      result.hasNoEvents shouldBe true
    }

    "handle account balance request" in {
      val accId = UUID.fromString("7fb24e42-9988-4ac6-a2c0-c5c53d2c4828")
      val acc = spawn(Account(accId, Account.State(10.50)))
      val initialState = State(Map(accId -> acc.ref))
      eventSourcedTestKit.initialize(initialState)

      val getAccBalance = GetAccountBalance(accId, _)
      val result = eventSourcedTestKit.runCommand(getAccBalance)

      result.reply shouldBe AccountBalance(10.50)
      result.hasNoEvents shouldBe true
    }

    "reject account balance request of non-existent account" in {
      val accId = UUID.fromString("43f23bd5-e3d9-4684-a2e4-2d47111119aa")
      val acc = createTestProbe[Account.Action]()
      val initialState = State(Map(accId -> acc.ref))
      eventSourcedTestKit.initialize(initialState)

      val nonexistentAccId = UUID.fromString("1b85eb74-3868-4173-b5b6-39b5ced4a9a4")
      val getAccBalance = GetAccountBalance(nonexistentAccId, _)
      val result = eventSourcedTestKit.runCommand(getAccBalance)

      result.reply shouldBe AccountBalanceNotFound
      result.hasNoEvents shouldBe true
    }

    "handle account balance request timeout" in {
      val accId = UUID.fromString("f2a68e5c-c5e1-41b0-b071-1f45b3a9961b")
      val acc = createTestProbe[Account.Action]()
      val initialState = State(Map(accId -> acc.ref))
      eventSourcedTestKit.initialize(initialState)

      val getAccBalance = GetAccountBalance(accId, _)
      val result = eventSourcedTestKit.runCommand(getAccBalance)

      result.reply shouldBe a[GetAccountBalanceFailure]
      result.hasNoEvents shouldBe true
    }

  }

  private def extractAccId(result: CommandResultWithReply[_, Event, _, _]): Id = {
    result.event match {
      case AccountCreated(_, accId) => accId
    }
  }
}
