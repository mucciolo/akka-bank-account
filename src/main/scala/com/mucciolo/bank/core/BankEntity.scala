package com.mucciolo.bank.core

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.EventSourcedBehavior.EventHandler
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.mucciolo.bank.core.BankEntity.Error.AccNotFound
import com.mucciolo.bank.serialization.CborSerializable
import com.mucciolo.bank.util.Generator

import java.util.UUID

object BankEntity {

  val EntityTypeKeyName: String = "Bank"
  type Id = UUID
  private val RandomIdGenerator: Generator[Id] = () => UUID.randomUUID()

  sealed trait Command extends CborSerializable
  final case class CreateAccount(replyTo: ActorRef[CreateAccountReply]) extends Command
  sealed trait UpdateAccountBalance extends Command {
    def accId: AccountEntity.Id
    def amount: BigDecimal
    def replyTo: ActorRef[StatusReply[AccountEntity.State]]
    def toAccountCommand: AccountEntity.Command
  }
  final case class Deposit(
    accId: AccountEntity.Id, amount: BigDecimal, replyTo: ActorRef[StatusReply[AccountEntity.State]]
  ) extends UpdateAccountBalance {
    override def toAccountCommand: AccountEntity.Command = AccountEntity.Deposit(amount, replyTo)
  }
  final case class Withdraw(
    accId: AccountEntity.Id, amount: BigDecimal, replyTo: ActorRef[StatusReply[AccountEntity.State]]
  ) extends UpdateAccountBalance {
    override def toAccountCommand: AccountEntity.Command = AccountEntity.Withdraw(amount, replyTo)
  }

  sealed trait Reply extends CborSerializable
  final case class CreateAccountReply(accId: AccountEntity.Id) extends Reply

  sealed trait Event extends CborSerializable
  final case class AccountCreated(accId: AccountEntity.Id) extends Event

  private type Account = ActorRef[AccountEntity.Command]
  final case class State(accountById: Map[AccountEntity.Id, Account]) extends CborSerializable
  object State {
    val Empty: State = State(accountById = Map.empty)
  }

  object Error {
    val AccNotFound = new NoSuchElementException("Account not found")
  }

  type ReplyEffect = akka.persistence.typed.scaladsl.ReplyEffect[Event, State]

  private val CommandHandler: (State, Command) => ReplyEffect = {

    val createAccountWithFixedGen = createAccount(RandomIdGenerator) _

    (state: State, cmd: Command) =>
      cmd match {
        case cmd: CreateAccount => createAccountWithFixedGen(state, cmd)
        case cmd: UpdateAccountBalance => accountBalanceUpdate(state, cmd)
      }
  }

  private def createAccount(idGenerator: Generator[Id])(state: State, cmd: CreateAccount): ReplyEffect = {
    val accId: AccountEntity.Id = idGenerator.nextNotIn(state.accountById.keySet)
    Effect.persist(AccountCreated(accId)).thenReply(cmd.replyTo)(_ => CreateAccountReply(accId))
  }

  private def accountBalanceUpdate(state: State, cmd: UpdateAccountBalance): ReplyEffect = {
    state.accountById.get(cmd.accId) match {
      case Some(account) =>
        cmd match {
          case Deposit(_, amount, replyTo) =>
            Effect.reply(account)(AccountEntity.Deposit(amount, replyTo))
          case Withdraw(_, amount, replyTo) =>
            Effect.reply(account)(AccountEntity.Withdraw(amount, replyTo))
        }
      case None =>
        Effect.reply(cmd.replyTo)(StatusReply.error(AccNotFound))
    }
  }

  private def eventHandler(ctx: ActorContext[Command]): EventHandler[State, Event] = (state, event) =>
    event match {
      case AccountCreated(accId) =>
        val account = ctx.spawn(AccountEntity(accId), s"acc-$accId")

        state.copy(state.accountById + (accId -> account))
    }

  def apply(id: Id): Behavior[Command] =
    Behaviors.setup { ctx =>
      EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId.of(EntityTypeKeyName, id.toString),
        emptyState = State.Empty,
        commandHandler = CommandHandler,
        eventHandler = eventHandler(ctx)
      )
    }
}
