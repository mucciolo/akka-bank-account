package com.mucciolo.bank.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import akka.util.Timeout
import cats.implicits._
import com.mucciolo.bank.core.BankEntity.CreateAccountReply
import com.mucciolo.bank.core.{AccountEntity, AccountEntityQuery, BankEntity}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._

final class BankRouter(bank: ActorRef[BankEntity.Command], query: AccountEntityQuery)(
  implicit scheduler: Scheduler
) {

  implicit val timeout: Timeout = Timeout(3.seconds)

  private def createAccount(): Future[CreateAccountReply] =
    bank.ask(BankEntity.CreateAccount)

  private def getAccountBalance(id: UUID): Future[Option[BigDecimal]] =
    query.getCurrentBalance(id)

  private def updateAccountBalance(
    id: UUID, request: AccountBalanceUpdateRequest
  ): Future[StatusReply[AccountEntity.State]] =
    bank.ask(replyTo => request.toCommand(id, replyTo))

  val routes: Route = Route.seal(
    pathPrefix("bank" / "accounts") {
      concat(
        pathEndOrSingleSlash {
          post {
            onSuccess(createAccount()) {
              case CreateAccountReply(accId) =>
                respondWithHeader(Location(s"/bank/accounts/$accId")) {
                  complete(StatusCodes.Created)
                }
            }
          }
        },
        path(JavaUUID) { accId =>
          concat(
            get {
              onSuccess(getAccountBalance(accId)) {
                case Some(balance) =>
                  complete(BankAccountBalance(balance))
                case None =>
                  complete(StatusCodes.NotFound)
              }
            },
            post {
              entity(as[AccountBalanceUpdateRequest]) { request =>
                onSuccess(updateAccountBalance(accId, request)) {
                  case StatusReply.Success(account: AccountEntity.State) =>
                    complete(BankAccountBalance(account.balance))
                  case StatusReply.Error(ex) =>
                    ex match {
                      case _: NoSuchElementException =>
                        complete(StatusCodes.NotFound)
                      case _: IllegalArgumentException =>
                        complete(StatusCodes.BadRequest, Error(s"${ex.getMessage}"))
                    }
                }
              }
            }
          )
        }
      )
    }
  )
}
