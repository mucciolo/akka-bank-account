package com.mucciolo.bank.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.EventSourcedBehavior.EventHandler
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.mucciolo.bank.core.AccountEntity.Error._
import com.mucciolo.bank.serialization.CborSerializable

object AccountEntity {

  val Zero: BigDecimal = BigDecimal(0.0)
  val EntityTypeKeyName: String = "Account"

  sealed trait Action extends CborSerializable

  sealed trait Command extends Action
  final case class Deposit(amount: PositiveAmount, replyTo: ActorRef[StatusReply[State]]) extends Command
  final case class Withdraw(amount: PositiveAmount, replyTo: ActorRef[StatusReply[State]]) extends Command

  sealed trait Query extends Action
  final case class GetBalance(replyTo: ActorRef[BigDecimal]) extends Query

  sealed trait Event extends CborSerializable
  final case class Deposited(amount: PositiveAmount) extends Event
  final case class Withdrawn(amount: PositiveAmount) extends Event

  final case class State(balance: BigDecimal) extends CborSerializable {
    def canWithdraw(amount: PositiveAmount): Boolean = balance - amount.value >= Zero
  }
  object State {
    val Empty: State = State(Zero)
  }

  object Error {
    val InsufficientFunds = new IllegalArgumentException("Insufficient funds")
  }

  type ReplyEffect = akka.persistence.typed.scaladsl.ReplyEffect[Event, State]

  private val ActionHandler: (State, Action) => ReplyEffect = (state, action) =>
    action match {
      case cmd: Deposit => deposit(cmd)
      case cmd: Withdraw => withdraw(state, cmd)
      case GetBalance(replyTo) => Effect.none.thenReply(replyTo)(_.balance)
    }

  private def deposit(deposit: Deposit): ReplyEffect = {
    Effect.persist(Deposited(deposit.amount))
      .thenReply(deposit.replyTo)(StatusReply.success)
  }

  private def withdraw(state: State, withdraw: Withdraw): ReplyEffect = {
    if (state.canWithdraw(withdraw.amount))
      Effect.persist(Withdrawn(withdraw.amount))
        .thenReply(withdraw.replyTo)(StatusReply.success)
    else
      Effect.reply(withdraw.replyTo)(StatusReply.error(InsufficientFunds))
  }

  private val EventHandler: EventHandler[State, Event] = (state, event) =>
    event match {
      case Deposited(amount) => state.copy(balance = state.balance + amount.value)
      case Withdrawn(amount) => state.copy(balance = state.balance - amount.value)
    }

  def apply(id: Id): Behavior[Action] =
    EventSourcedBehavior.withEnforcedReplies[Action, Event, State](
      persistenceId = PersistenceId.of(EntityTypeKeyName, id.toString),
      emptyState = State.Empty,
      commandHandler = ActionHandler,
      eventHandler = EventHandler
    )

}
