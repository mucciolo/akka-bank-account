package com.mucciolo.bank

import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.CommandResultWithReply
import com.mucciolo.bank.core.BankEntity.Error.AccNotFound
import com.mucciolo.bank.core.BankEntity._
import com.mucciolo.bank.core.{AccountEntity, BankEntity}

import java.util.UUID

final class BankEntitySpec extends ActorSpecBase[Command, Event, State] {

  private val bankId = UUID.fromString("5d624393-4576-4c45-bcfd-e0e5dc327b40")
  override protected val eventSourcedTestKit: EventSourcedBehaviorTestKit[Command, Event, State] =
    EventSourcedBehaviorTestKit(system, BankEntity(bankId))

  "BankEntity" should {
    "create one account" in {

      val cmd = CreateAccount(_)
      val result = eventSourcedTestKit.runCommand(cmd)

      val accId = extractAccId(result)
      result.reply shouldBe CreateAccountReply(accId)
      result.state.accountById.keySet should contain only accId

    }

    "create two accounts" in {

      val firstAccId = UUID.fromString("3f86b1ef-c547-40a5-80ed-fd5d4945d7e4")
      val firstAcc = spawn(AccountEntity(firstAccId), "first-account")
      val initialState = State(Map(firstAccId -> firstAcc))
      eventSourcedTestKit.initialize(initialState)

      val cmd = CreateAccount(_)
      val result = eventSourcedTestKit.runCommand(cmd)
      val secondAccId = extractAccId(result)

      result.reply shouldBe CreateAccountReply(secondAccId)
      result.state.accountById.keySet should contain only (firstAccId, secondAccId)

    }

    "pipe deposit to account" in {

      val accId = UUID.fromString("3f86b1ef-c547-40a5-80ed-fd5d4945d7e4")
      val acc = createTestProbe[AccountEntity.Command]()
      val otherAccId = UUID.fromString("ab219812-d5e4-4869-9916-04366590db57")
      val otherAcc = createTestProbe[AccountEntity.Command]().ref
      val initialState = State(Map(
        accId -> acc.ref,
        otherAccId -> otherAcc
      ))
      eventSourcedTestKit.initialize(initialState)

      val requester = createTestProbe[StatusReply[AccountEntity.State]]()
      val cmd = BankEntity.Deposit(accId, 10.55, requester.ref)
      val result = eventSourcedTestKit.runCommand(cmd)

      acc.expectMessage(AccountEntity.Deposit(10.55, requester.ref))
      result.hasNoEvents shouldBe true

    }

    "reject deposit to non-existent account" in {

      val oneAccId = UUID.fromString("3f86b1ef-c547-40a5-80ed-fd5d4945d7e4")
      val anotherAccId = UUID.fromString("ab219812-d5e4-4869-9916-04366590db57")
      val initialState = State(Map(
        oneAccId -> createTestProbe[AccountEntity.Command]().ref,
        anotherAccId -> createTestProbe[AccountEntity.Command]().ref
      ))
      eventSourcedTestKit.initialize(initialState)

      val nonexistentAccId = UUID.fromString("9fa33c2c-5720-49e6-9946-ce90763d9f33")
      val cmd = BankEntity.Deposit(nonexistentAccId, 2.34, _)
      val result = eventSourcedTestKit.runCommand(cmd)

      result.reply shouldBe StatusReply.error(AccNotFound)
      result.hasNoEvents shouldBe true

    }

    "pipe withdraw to account" in {

      val accId = UUID.fromString("8781d33e-f2f0-4ba4-af18-1b492055826a")
      val acc = createTestProbe[AccountEntity.Command]()
      val anotherAccId = UUID.fromString("c6247c6a-440a-4d8b-907c-dd996c1ee9df")
      val initialState = State(Map(
        accId -> acc.ref,
        anotherAccId -> createTestProbe[AccountEntity.Command]().ref
      ))
      eventSourcedTestKit.initialize(initialState)

      val requester = createTestProbe[StatusReply[AccountEntity.State]]()
      val cmd = BankEntity.Withdraw(accId, 5.07, requester.ref)
      val result = eventSourcedTestKit.runCommand(cmd)

      acc.expectMessage(AccountEntity.Withdraw(5.07, requester.ref))
      result.hasNoEvents shouldBe true

    }

    "reject withdraw to non-existent account" in {

      val oneAccId = UUID.fromString("b36aa1b2-3f9b-4258-a51b-69127d6fd5fd")
      val anotherAccId = UUID.fromString("76917ff5-ded7-4cc4-928b-0370ce64e017")
      val initialState = State(Map(
        oneAccId -> createTestProbe[AccountEntity.Command]().ref,
        anotherAccId -> createTestProbe[AccountEntity.Command]().ref
      ))
      eventSourcedTestKit.initialize(initialState)

      val accId = UUID.fromString("0572cd52-802d-4982-b3f3-4137c5997840")
      val cmd = BankEntity.Withdraw(accId, 350.00, _)
      val result = eventSourcedTestKit.runCommand(cmd)

      result.reply shouldBe StatusReply.error(AccNotFound)
      result.hasNoEvents shouldBe true

    }

  }

  private def extractAccId(result: CommandResultWithReply[_, Event, _, _]): AccountEntity.Id = {
    result.event match {
      case AccountCreated(accId) => accId
    }
  }
}
