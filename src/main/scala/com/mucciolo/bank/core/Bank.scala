package com.mucciolo.bank.core

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.EventSourcedBehavior.EventHandler
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.util.Timeout
import com.mucciolo.bank.serialization.CborSerializable
import com.mucciolo.bank.util.Generator

import java.time.Instant
import java.util.UUID
import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object Bank {

  val EntityTypeKeyName: String = "Bank"
  private val RandomIdGenerator: Generator[Id] = () => UUID.randomUUID()

  sealed trait Action extends CborSerializable

  sealed trait Command extends Action
  final case class CreateAccount(replyTo: ActorRef[CreateAccountResponse]) extends Command

  final case class Deposit(
    accId: Id, amount: PositiveAmount, replyTo: ActorRef[DepositResponse]
  ) extends Command
  private final case class AccountDepositSuccess(response: Account.DepositSuccess) extends Command
  private final case class AccountDepositFailure(transactionId: Id, reason: String) extends Command
  sealed trait DepositResponse extends CborSerializable
  final case class DepositSuccess(updatedBalance: BigDecimal) extends DepositResponse
  final case class DepositFailure(reason: String) extends DepositResponse
  object DepositAccountNotFound extends DepositResponse

  final case class Withdraw(
    accId: Id, amount: PositiveAmount, replyTo: ActorRef[WithdrawResponse]
  ) extends Command
  private final case class AccountWithdrawResponse(response: Account.WithdrawResponse) extends Command
  private final case class AccountWithdrawFailure(transactionId: Id, reason: String) extends Command
  sealed trait WithdrawResponse extends CborSerializable
  final case class WithdrawSuccess(updatedBalance: BigDecimal) extends WithdrawResponse
  final case class WithdrawFailure(reason: String) extends WithdrawResponse
  object WithdrawInsufficientFunds extends WithdrawResponse
  object WithdrawAccountNotFound extends WithdrawResponse

  sealed trait Query extends Action
  final case class GetAccountBalance(accId: Id, replyTo: ActorRef[GetAccountBalanceResponse]) extends Query
  private final case class AccountGetBalanceResponse(response: Account.GetBalanceResponse) extends Command
  private final case class AccountGetBalanceFailure(queryId: Id, reason: String) extends Command
  sealed trait GetAccountBalanceResponse extends CborSerializable
  final case class AccountBalance(balance: BigDecimal) extends GetAccountBalanceResponse
  final case class GetAccountBalanceFailure(reason: String) extends GetAccountBalanceResponse
  case object AccountBalanceNotFound extends GetAccountBalanceResponse

  sealed trait Response extends CborSerializable
  final case class CreateAccountResponse(accId: Id) extends Response

  sealed trait Event extends CborSerializable {
    val timestamp: Instant
  }
  final case class AccountCreated(timestamp: Instant, accId: Id) extends Event

  final case class State(accountById: Map[Id, ActorRef[Account.Action]]) extends CborSerializable
  object State {
    val Empty: State = State(accountById = Map.empty)
  }

  type ReplyEffect = akka.persistence.typed.scaladsl.ReplyEffect[Event, State]

  private def actionHandler(ctx: ActorContext[Action])(implicit timeout: Timeout): (State, Action) => ReplyEffect = {

    val pendingDeposits: mutable.Map[UUID, ActorRef[DepositResponse]] = mutable.Map.empty
    val pendingWithdraws: mutable.Map[UUID, ActorRef[WithdrawResponse]] = mutable.Map.empty
    val pendingBalanceQueries: mutable.Map[UUID, ActorRef[GetAccountBalanceResponse]] = mutable.Map.empty
    val createAccountWithFixedGen = createAccount(RandomIdGenerator) _

    (state, action) =>
      action match {
        case createAccount: CreateAccount =>
          createAccountWithFixedGen(state, createAccount)

        case Deposit(accId, amount, replyTo) =>
          state.accountById.get(accId) match {
            case Some(account) =>

              val transactionId = UUID.randomUUID()
              pendingDeposits.put(transactionId, replyTo)

              ctx.ask(account, Account.Deposit(transactionId, amount, _)) {
                case Success(depositResponse) =>
                  AccountDepositSuccess(depositResponse)
                case Failure(th) =>
                  AccountDepositFailure(transactionId, th.getMessage)
              }

              Effect.noReply

            case None =>
              Effect.reply(replyTo)(DepositAccountNotFound)
          }

        case AccountDepositSuccess(successResponse) =>
          pendingDeposits.remove(successResponse.transactionId) match {
            case Some(replyTo) =>
              Effect.reply(replyTo)(DepositSuccess(successResponse.updatedBalance))
            case None =>
              ctx.log.info("Deposit {} is not pending but a successful response was received.", successResponse.transactionId)
              Effect.noReply
          }

        case AccountDepositFailure(transactionId, reason) =>
          pendingDeposits.remove(transactionId) match {
            case Some(replyTo) =>
              ctx.log.error("Deposit {} failed. Reason: {}", transactionId, reason)
              Effect.reply(replyTo)(DepositFailure(reason))
            case None =>
              ctx.log.info("Deposit {} is not pending but a failure response was received. Reason: {}", transactionId, reason)
              Effect.noReply
          }

        case Withdraw(accId, amount, replyTo) =>
          state.accountById.get(accId) match {
            case Some(account) =>

              val transactionId = UUID.randomUUID()
              pendingWithdraws.put(transactionId, replyTo)

              ctx.ask(account, Account.Withdraw(transactionId, amount, _)) {
                case Success(withdrawResponse) =>
                  AccountWithdrawResponse(withdrawResponse)
                case Failure(th) =>
                  AccountWithdrawFailure(transactionId, th.getMessage)
              }

              Effect.noReply

            case None =>
              Effect.reply(replyTo)(WithdrawAccountNotFound)
          }

        case AccountWithdrawResponse(response) =>
          pendingWithdraws.remove(response.transactionId) match {
            case Some(replyTo) =>
              response match {
                case Account.WithdrawSuccess(_, updatedBalance) =>
                  Effect.reply(replyTo)(WithdrawSuccess(updatedBalance))
                case Account.InsufficientFunds(_) =>
                  Effect.reply(replyTo)(WithdrawInsufficientFunds)
              }
            case None =>
              ctx.log.info("Withdrawal {} is not pending but a response was received.", response.transactionId)
              Effect.noReply
          }

        case AccountWithdrawFailure(transactionId, reason) =>
          pendingWithdraws.remove(transactionId) match {
            case Some(replyTo) =>
              ctx.log.error("Withdrawal {} failed. Reason: {}", transactionId, reason)
              Effect.reply(replyTo)(WithdrawFailure(reason))
            case None =>
              ctx.log.info("Withdrawal {} is not pending but a failure response was received. Reason: {}", transactionId, reason)
              Effect.noReply
          }

        case GetAccountBalance(accId, replyTo) =>

          val queryId = UUID.randomUUID()
          pendingBalanceQueries.put(queryId, replyTo)

          state.accountById.get(accId) match {
            case Some(account) =>
              ctx.ask(account, Account.GetBalance(queryId, _)) {
                case Success(response) => AccountGetBalanceResponse(response)
                case Failure(th) => AccountGetBalanceFailure(queryId, th.getMessage)
              }
              Effect.noReply
            case None =>
              Effect.reply(replyTo)(AccountBalanceNotFound)
          }

        case AccountGetBalanceResponse(response) =>
          pendingBalanceQueries.remove(response.queryId) match {
            case Some(replyTo) =>
              Effect.reply(replyTo)(AccountBalance(response.balance))
            case None =>
              ctx.log.info("Account balance request {} is not pending but a response was received.", response.queryId)
              Effect.noReply
          }

        case AccountGetBalanceFailure(queryId, reason) =>
          pendingBalanceQueries.remove(queryId) match {
            case Some(replyTo) =>
              ctx.log.error("Account balance request {} failed. Reason: {}", queryId, reason)
              Effect.reply(replyTo)(GetAccountBalanceFailure(reason))
            case None =>
              ctx.log.info("Account balance request {} is not pending but a failure response was received. Reason: {}", queryId, reason)
              Effect.noReply
          }
      }
  }

  private def createAccount(idGenerator: Generator[Id])(state: State, cmd: CreateAccount): ReplyEffect = {
    val timestamp = Instant.now()
    val accId: Id = idGenerator.nextNotIn(state.accountById.keySet)
    Effect.persist(AccountCreated(timestamp, accId)).thenReply(cmd.replyTo)(_ => CreateAccountResponse(accId))
  }

  private def eventHandler(ctx: ActorContext[_]): EventHandler[State, Event] = (state, event) =>
    event match {
      case AccountCreated(_, accId) =>
        val account = ctx.spawn(Account(accId), s"acc-$accId")

        state.copy(state.accountById + (accId -> account))
    }

  def apply(id: Id)(implicit timeout: Timeout = Timeout(3.seconds)): Behavior[Action] =
    Behaviors.setup { ctx =>
      EventSourcedBehavior.withEnforcedReplies[Action, Event, State](
        persistenceId = PersistenceId.of(EntityTypeKeyName, id.toString),
        emptyState = State.Empty,
        commandHandler = actionHandler(ctx),
        eventHandler = eventHandler(ctx)
      )
    }
}
