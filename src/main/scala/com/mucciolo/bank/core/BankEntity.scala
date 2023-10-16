package com.mucciolo.bank.core

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.EventSourcedBehavior.EventHandler
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.mucciolo.bank.core.BankEntity.Error.AccountNotFound
import com.mucciolo.bank.serialization.CborSerializable
import com.mucciolo.bank.util.Generator

import java.time.Instant
import java.util.UUID
import scala.util.control.NoStackTrace

object BankEntity {

  val EntityTypeKeyName: String = "Bank"
  private val RandomIdGenerator: Generator[Id] = () => UUID.randomUUID()

  sealed trait Command extends CborSerializable
  final case class CreateAccount(replyTo: ActorRef[CreateAccountReply]) extends Command
  sealed trait UpdateAccountBalance extends Command {
    val accId: Id
    val amount: PositiveAmount
    val replyTo: ActorRef[StatusReply[AccountEntity.State]]
    def toAccountCommand: AccountEntity.Command
  }
  final case class Deposit(
    accId: Id, amount: PositiveAmount, replyTo: ActorRef[StatusReply[AccountEntity.State]]
  ) extends UpdateAccountBalance {
    override def toAccountCommand: AccountEntity.Command = AccountEntity.Deposit(amount, replyTo)
  }
  final case class Withdraw(
    accId: Id, amount: PositiveAmount, replyTo: ActorRef[StatusReply[AccountEntity.State]]
  ) extends UpdateAccountBalance {
    override def toAccountCommand: AccountEntity.Command = AccountEntity.Withdraw(amount, replyTo)
  }

  sealed trait Reply extends CborSerializable
  final case class CreateAccountReply(accId: Id) extends Reply

  sealed trait Event extends CborSerializable {
    val timestamp: Instant
  }
  final case class AccountCreated(timestamp: Instant, accId: Id) extends Event

  private type Account = ActorRef[AccountEntity.Command]
  final case class State(accountById: Map[Id, Account]) extends CborSerializable
  object State {
    val Empty: State = State(accountById = Map.empty)
  }

  object Error {
    object AccountNotFound extends NoStackTrace
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
    val timestamp = Instant.now()
    val accId: Id = idGenerator.nextNotIn(state.accountById.keySet)
    Effect.persist(AccountCreated(timestamp, accId)).thenReply(cmd.replyTo)(_ => CreateAccountReply(accId))
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
        Effect.reply(cmd.replyTo)(StatusReply.error(AccountNotFound))
    }
  }

  private def eventHandler(ctx: ActorContext[Command]): EventHandler[State, Event] = (state, event) =>
    event match {
      case AccountCreated(_, accId) =>
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
