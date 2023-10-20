package com.mucciolo.bank

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.stream.scaladsl.Source
import cats.implicits.catsSyntaxOptionId
import com.mucciolo.bank.core.Bank._
import com.mucciolo.bank.core.{AccountQuery, AccountStatement, AccountStatementEntry, Bank}
import com.mucciolo.bank.http.BankRouter
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe.Json

import java.time.Instant
import java.util.UUID

final class BankRouterSpec extends RouteSpec {

  import akka.actor.typed.scaladsl.adapter._

  private implicit val typedSystem: ActorSystem[Nothing] = system.toTyped

  private val bank = TestProbe[Bank.Action]()
  private val bankAccountQuery = mock[AccountQuery]
  private val router = new BankRouter(bank.ref, bankAccountQuery)
  private val routes = router.routes

  "Bank routes" when {
    "[POST /bank/accounts] creating account" should {

      val accId = UUID.fromString("f1a64587-9786-4b17-b357-1d1f0e61fc30")

      "return 201" in {
        val testResult = Post("/bank/accounts") ~> routes
        val createAccountMsg = bank.expectMessageType[CreateAccount]

        createAccountMsg.replyTo ! CreateAccountResponse(accId)

        testResult ~> check {
          status shouldBe StatusCodes.Created
          header[Location].value.uri shouldBe Uri(s"/bank/accounts/$accId")
        }
      }
    }

    "[GET /bank/accounts/:id] fetching account" should {

      val accId = UUID.fromString("5f360fe7-41c2-4325-b0d2-80f2ce220471")

      "pass the requested account id to the bank " in {
        Get(s"/bank/accounts/$accId") ~> routes
        bank.expectMessageType[GetAccountBalance].accId shouldBe accId
      }

      "return 200 given existent account" in {
        val testResult = Get(s"/bank/accounts/$accId") ~> routes
        val getAccountBalanceMsg = bank.expectMessageType[GetAccountBalance]

        getAccountBalanceMsg.replyTo ! AccountBalance(11.50)

        testResult ~> check {
          status shouldBe StatusCodes.OK
          responseAs[Json] shouldBe Json.obj(
            "balance" -> Json.fromDoubleOrNull(11.50)
          )
        }
      }

      "return 404 given non-existent account" in {
        val testResult = Get(s"/bank/accounts/$accId") ~> routes
        val getAccountBalanceMsg = bank.expectMessageType[GetAccountBalance]

        getAccountBalanceMsg.replyTo ! AccountBalanceNotFound

        testResult ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }

      "return 500 given failure" in {
        val testResult = Get(s"/bank/accounts/$accId") ~> routes
        val getAccountBalanceMsg = bank.expectMessageType[GetAccountBalance]

        getAccountBalanceMsg.replyTo ! GetAccountBalanceFailure("Something happened.")

        testResult ~> check {
          status shouldBe StatusCodes.InternalServerError
        }
      }
    }

    "[POST /bank/accounts/:id] updating account balance" should {

      val accId = UUID.fromString("ebb4e542-3659-4628-8379-52f57a405024")

      "pass the requested account id to the bank when the amount is positive" in {
        val accBalanceUpdateRequest = Json.obj(
          "amount" -> Json.fromDoubleOrNull(1.00)
        )
        Post(s"/bank/accounts/$accId", accBalanceUpdateRequest) ~> routes

        bank.expectMessageType[Deposit].accId shouldBe accId
      }

      "pass the requested account id to the bank when the amount is negative" in {
        val accBalanceUpdateRequest = Json.obj(
          "amount" -> Json.fromDoubleOrNull(-1.00)
        )
        Post(s"/bank/accounts/$accId", accBalanceUpdateRequest) ~> routes

        bank.expectMessageType[Withdraw].accId shouldBe accId
      }

      "return 200 given amount is positive (deposit)" in {
        val accBalanceUpdateRequest = Json.obj(
          "amount" -> Json.fromDoubleOrNull(5.10)
        )
        val testResult = Post(s"/bank/accounts/$accId", accBalanceUpdateRequest) ~> routes
        val depositMsg = bank.expectMessageType[Deposit]

        depositMsg.replyTo ! Bank.DepositSuccess(updatedBalance = 22.75)

        testResult ~> check {
          status shouldBe StatusCodes.OK
          responseAs[Json] shouldBe Json.obj(
            "balance" -> Json.fromDoubleOrNull(22.75)
          )
        }
      }

      "return 200 given amount is negative (withdrawal)" in {
        val accBalanceUpdateRequest = Json.obj(
          "amount" -> Json.fromDoubleOrNull(-20.00)
        )
        val testResult = Post(s"/bank/accounts/$accId", accBalanceUpdateRequest) ~> routes
        val withdrawMsg = bank.expectMessageType[Withdraw]

        withdrawMsg.replyTo ! Bank.WithdrawSuccess(updatedBalance = 55.08)

        testResult ~> check {
          status shouldBe StatusCodes.OK
          responseAs[Json] shouldBe Json.obj(
            "balance" -> Json.fromDoubleOrNull(55.08)
          )
        }
      }

      "return 400 given amount is zero" in {
        val accBalanceUpdateRequest = Json.obj(
          "amount" -> Json.fromDoubleOrNull(0.00)
        )
        val testResult = Post(s"/bank/accounts/$accId", accBalanceUpdateRequest) ~> routes

        bank.expectNoMessage()

        testResult ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }

      "return 400 given amount is null" in {
        val accBalanceUpdateRequest = Json.obj(
          "amount" -> Json.Null
        )
        val testResult = Post(s"/bank/accounts/$accId", accBalanceUpdateRequest) ~> routes

        bank.expectNoMessage()

        testResult ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }

      "return 400 given entity is null" in {
        val accBalanceUpdateRequest = Json.Null
        val testResult = Post(s"/bank/accounts/$accId", accBalanceUpdateRequest) ~> routes

        bank.expectNoMessage()

        testResult ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }

      "return 404 given positive amount but non-existent account" in {
        val accBalanceUpdateRequest = Json.obj(
          "amount" -> Json.fromDoubleOrNull(50.00)
        )
        val testResult = Post(s"/bank/accounts/$accId", accBalanceUpdateRequest) ~> routes
        val withdrawMsg = bank.expectMessageType[Deposit]

        withdrawMsg.replyTo ! Bank.DepositAccountNotFound

        testResult ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }

      "return 404 given negative amount but non-existent account" in {
        val accBalanceUpdateRequest = Json.obj(
          "amount" -> Json.fromDoubleOrNull(-100.00)
        )
        val testResult = Post(s"/bank/accounts/$accId", accBalanceUpdateRequest) ~> routes
        val withdrawMsg = bank.expectMessageType[Withdraw]

        withdrawMsg.replyTo ! Bank.WithdrawAccountNotFound

        testResult ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }

      "return 500 on failure given positive amount" in {
        val accBalanceUpdateRequest = Json.obj(
          "amount" -> Json.fromDoubleOrNull(50.00)
        )
        val testResult = Post(s"/bank/accounts/$accId", accBalanceUpdateRequest) ~> routes
        val withdrawMsg = bank.expectMessageType[Deposit]

        withdrawMsg.replyTo ! Bank.DepositFailure("Something happened.")

        testResult ~> check {
          status shouldBe StatusCodes.InternalServerError
        }
      }

      "return 500 on failure given negative amount" in {
        val accBalanceUpdateRequest = Json.obj(
          "amount" -> Json.fromDoubleOrNull(-10.00)
        )
        val testResult = Post(s"/bank/accounts/$accId", accBalanceUpdateRequest) ~> routes
        val withdrawMsg = bank.expectMessageType[Withdraw]

        withdrawMsg.replyTo ! Bank.WithdrawFailure("Something happened.")

        testResult ~> check {
          status shouldBe StatusCodes.InternalServerError
        }
      }
    }

    "[GET /bank/accounts/:id/statements] statement request" should {

      val accId = UUID.fromString("8e2db55c-c755-43ad-ba8c-5a30ed3c39b5")

      "return 200 on empty statement " in {

        val emptyStatement = AccountStatement(List.empty)

        bankAccountQuery.getAccountStatement _ expects accId returning Source.single(emptyStatement)

        Get(s"/bank/accounts/$accId/statements") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[Json] shouldBe Json.obj(
            "entries" -> Json.arr()
          )
        }
      }

      "return 200 on non-empty statement " in {

        val statement = AccountStatement(
          List(
            AccountStatementEntry(
              timestamp = Instant.ofEpochSecond(1),
              amount = 10.00,
            ),
            AccountStatementEntry(
              timestamp = Instant.ofEpochSecond(2),
              amount = -5.00
            )
          )
        )

        bankAccountQuery.getAccountStatement _ expects accId returning Source.single(statement)

        Get(s"/bank/accounts/$accId/statements") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[Json] shouldBe Json.obj(
            "entries" -> Json.arr(
              Json.obj(
                "timestamp" -> Json.fromString("1970-01-01T00:00:01Z"),
                "amount" -> Json.fromDoubleOrNull(10.00)
              ),
              Json.obj(
                "timestamp" -> Json.fromString("1970-01-01T00:00:02Z"),
                "amount" -> Json.fromDoubleOrNull(-5.00)
              )
            )
          )
        }
      }
    }
  }
}
