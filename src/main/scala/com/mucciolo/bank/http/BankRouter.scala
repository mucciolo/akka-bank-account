package com.mucciolo.bank.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import akka.util.{ByteString, Timeout}
import cats.implicits._
import com.mucciolo.bank.core.Bank._
import com.mucciolo.bank.core.{AccountQuery, Bank}
import com.mucciolo.bank.http.codecs.accountStatementEncoder
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import eu.timepit.refined.auto._
import eu.timepit.refined.refineV

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._

final class BankRouter(
  bank: ActorRef[Bank.Action],
  query: AccountQuery
)(implicit scheduler: Scheduler) {

  private implicit val timeout: Timeout = Timeout(3.seconds)

  private val completeWithInternalServerError =
    complete(StatusCodes.InternalServerError, Error("Don't worry, this is on us!"))

  private implicit val jsonStreamingSupport: JsonEntityStreamingSupport =
    EntityStreamingSupport.json().withFramingRenderer(Flow[ByteString].intersperse(ByteString("\n")))

  val routes: Route = Route.seal(
    pathPrefix("bank" / "accounts") {
      concat(
        pathEnd {
          post {
            onSuccess(createAccount()) {
              case CreateAccountResponse(accId) =>
                respondWithHeader(Location(s"/bank/accounts/$accId")) {
                  complete(StatusCodes.Created)
                }
            }
          }
        },
        pathPrefix(JavaUUID) { accId =>
          concat(
            pathEnd {
              concat(
                get {
                  onSuccess(getAccountBalance(accId)) {
                    case AccountBalance(balance) =>
                      complete(BankAccountBalance(balance))
                    case GetAccountBalanceFailure(_) =>
                      completeWithInternalServerError
                    case Bank.AccountBalanceNotFound =>
                      complete(StatusCodes.NotFound)
                  }
                },
                post {
                  entity(as[AccountBalanceUpdateRequest]) { request =>
                    if (request.amount > 0) {
                      onSuccess(deposit(accId, request.amount)) {
                        case DepositSuccess(updatedBalance) =>
                          complete(BankAccountBalance(updatedBalance))
                        case DepositAccountNotFound =>
                          complete(StatusCodes.NotFound)
                        case DepositFailure(_) =>
                          completeWithInternalServerError
                      }
                    } else { // amount < 0
                      onSuccess(withdraw(accId, request.amount)) {
                        case WithdrawSuccess(updatedBalance) =>
                          complete(BankAccountBalance(updatedBalance))
                        case WithdrawFailure(_) =>
                          completeWithInternalServerError
                        case WithdrawInsufficientFunds =>
                          complete(StatusCodes.BadRequest, Error("Insufficient funds."))
                        case WithdrawAccountNotFound =>
                          complete(StatusCodes.NotFound)
                      }
                    }
                  }
                }
              )
            },
            path("statements") {
              get {
                complete(query.getAccountStatement(accId))
              }
            }
          )
        }
      )
    }
  )

  private def createAccount(): Future[CreateAccountResponse] =
    bank.ask(CreateAccount)

  private def getAccountBalance(accId: UUID): Future[GetAccountBalanceResponse] =
    bank.ask(GetAccountBalance(accId, _))

  private def withdraw(accId: UUID, amount: NonZeroBigDecimal): Future[WithdrawResponse] =
    bank.ask(Withdraw(accId, refineV.unsafeFrom(-amount), _))

  private def deposit(accId: UUID, amount: NonZeroBigDecimal): Future[DepositResponse] =
    bank.ask(Deposit(accId, refineV.unsafeFrom(amount), _))

}
