package com.mucciolo.bank.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.EventSourcedBehavior.EventHandler
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.mucciolo.bank.core.AccountEntity.Error._
import com.mucciolo.bank.serialization.CborSerializable

import java.util.UUID

object AccountEntity {

  val Zero: BigDecimal = BigDecimal(0.0)
  val EntityTypeKeyName: String = "Account"
  type Id = UUID

  sealed trait Command extends CborSerializable
  // TODO use refined to restrict amount to positive values only
  final case class Deposit(amount: BigDecimal, replyTo: ActorRef[StatusReply[State]]) extends Command
  final case class Withdraw(amount: BigDecimal, replyTo: ActorRef[StatusReply[State]]) extends Command

  sealed trait Event extends CborSerializable
  final case class Deposited(amount: BigDecimal) extends Event
  final case class Withdrawn(amount: BigDecimal) extends Event

  // TODO research https://github.com/typelevel/squants to model balance
  final case class State(balance: BigDecimal) extends CborSerializable {
    def canWithdraw(amount: BigDecimal): Boolean = balance - amount >= Zero
  }
  object State {
    val Empty: State = State(Zero)
  }

  object Error {
    val InsufficientFunds = new IllegalArgumentException("Insufficient funds")
    val InvalidDeposit = new IllegalArgumentException("Deposit amount must be positive")
    val InvalidWithdraw = new IllegalArgumentException("Withdraw amount must be positive")
  }

  type ReplyEffect = akka.persistence.typed.scaladsl.ReplyEffect[Event, State]

  private val CommandHandler: (State, Command) => ReplyEffect = (state, cmd) =>
    cmd match {
      case cmd: Deposit => deposit(cmd)
      case cmd: Withdraw => withdraw(state, cmd)
    }

  private def deposit(deposit: Deposit): ReplyEffect = {
    if (deposit.amount > 0)
      Effect.persist(Deposited(deposit.amount))
        .thenReply(deposit.replyTo)(StatusReply.success)
    else
      Effect.reply(deposit.replyTo)(StatusReply.error(InvalidDeposit))
  }

  private def withdraw(state: State, withdraw: Withdraw): ReplyEffect = {
    if (withdraw.amount > 0) {
      if (state.canWithdraw(withdraw.amount))
        Effect.persist(Withdrawn(withdraw.amount))
          .thenReply(withdraw.replyTo)(StatusReply.success)
      else
        Effect.reply(withdraw.replyTo)(StatusReply.error(InsufficientFunds))
    } else {
      Effect.reply(withdraw.replyTo)(StatusReply.error(InvalidWithdraw))
    }
  }

  private val EventHandler: EventHandler[State, Event] = (state, event) =>
    event match {
      case Deposited(amount) => state.copy(balance = state.balance + amount)
      case Withdrawn(amount) => state.copy(balance = state.balance - amount)
    }

  def apply(id: Id): Behavior[Command] =
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
      persistenceId = PersistenceId.of(EntityTypeKeyName, id.toString),
      emptyState = State.Empty,
      commandHandler = CommandHandler,
      eventHandler = EventHandler
    )

}
