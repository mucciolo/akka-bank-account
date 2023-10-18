package com.mucciolo.bank.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.EventSourcedBehavior.EventHandler
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.mucciolo.bank.serialization.CborSerializable

object Account {

  val Zero: BigDecimal = BigDecimal(0.0)
  val EntityTypeKeyName: String = "Account"

  sealed trait Action extends CborSerializable

  sealed trait Command extends Action
  final case class Deposit(transactionId: Id, amount: PositiveAmount, replyTo: ActorRef[DepositSuccess]) extends Command
  final case class DepositSuccess(transactionId: Id, updatedBalance: BigDecimal) extends CborSerializable

  final case class Withdraw(transactionId: Id, amount: PositiveAmount, replyTo: ActorRef[WithdrawResponse]) extends Command
  sealed trait WithdrawResponse extends CborSerializable {
    val transactionId: Id
  }
  final case class WithdrawSuccess(transactionId: Id, updatedBalance: BigDecimal) extends WithdrawResponse
  final case class InsufficientFunds(transactionId: Id) extends WithdrawResponse

  sealed trait Query extends Action
  final case class GetBalance(queryId: Id, replyTo: ActorRef[GetBalanceResponse]) extends Query
  final case class GetBalanceResponse(queryId: Id, balance: BigDecimal) extends CborSerializable

  sealed trait Event extends CborSerializable
  final case class Deposited(amount: PositiveAmount) extends Event
  final case class Withdrawn(amount: PositiveAmount) extends Event

  final case class State(balance: BigDecimal) extends CborSerializable {
    def canWithdraw(amount: PositiveAmount): Boolean = balance - amount.value >= Zero
  }
  object State {
    val Empty: State = State(Zero)
  }

  type ReplyEffect = akka.persistence.typed.scaladsl.ReplyEffect[Event, State]

  private val ActionHandler: (State, Action) => ReplyEffect = (state, action) =>
    action match {
      case Deposit(transactionId, amount, replyTo) =>
        Effect.persist(Deposited(amount))
          .thenReply(replyTo)(updatedState => DepositSuccess(transactionId, updatedState.balance))

      case Withdraw(transactionId, amount, replyTo) =>
        if (state.canWithdraw(amount))
          Effect.persist(Withdrawn(amount))
            .thenReply(replyTo)(updatedState => WithdrawSuccess(transactionId, updatedState.balance))
        else
          Effect.reply(replyTo)(InsufficientFunds(transactionId))

      case GetBalance(queryId, replyTo) =>
        Effect.none
          .thenReply(replyTo)(state => GetBalanceResponse(queryId, state.balance))
    }

  private val EventHandler: EventHandler[State, Event] = (state, event) =>
    event match {
      case Deposited(amount) => state.copy(balance = state.balance + amount.value)
      case Withdrawn(amount) => state.copy(balance = state.balance - amount.value)
    }

  def apply(id: Id, initialState: State = State.Empty): Behavior[Action] =
    EventSourcedBehavior.withEnforcedReplies[Action, Event, State](
      persistenceId = PersistenceId.of(EntityTypeKeyName, id.toString),
      emptyState = initialState,
      commandHandler = ActionHandler,
      eventHandler = EventHandler
    )

}
