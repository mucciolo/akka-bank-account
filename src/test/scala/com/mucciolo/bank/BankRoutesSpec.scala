package com.mucciolo.bank

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.pattern.StatusReply
import cats.implicits.catsSyntaxOptionId
import com.mucciolo.bank.core.BankEntity.{CreateAccount, CreateAccountReply, Deposit, Withdraw}
import com.mucciolo.bank.core.{AccountEntity, AccountEntityQuery, BankEntity}
import com.mucciolo.bank.http.BankRouter
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe.Json

import java.util.UUID
import scala.concurrent.Future

final class BankRoutesSpec extends RouteSpec {

  import akka.actor.typed.scaladsl.adapter._

  private implicit val typedSystem: ActorSystem[_] = system.toTyped

  private val bank = TestProbe[BankEntity.Command]()
  private val bankAccountQuery = mock[AccountEntityQuery]
  private val router = new BankRouter(bank.ref, bankAccountQuery)
  private val routes = router.routes

  "Bank routes" when {
    "[POST /bank/accounts] creating account" should {
      "return 201" in {

        val testResult = Post("/bank/accounts") ~> routes
        val createAccountMsg = bank.expectMessageType[CreateAccount]
        val accId = UUID.fromString("f1a64587-9786-4b17-b357-1d1f0e61fc30")
        createAccountMsg.replyTo ! CreateAccountReply(accId)

        testResult ~> check {
          status shouldBe StatusCodes.Created
          header[Location].value.uri shouldBe Uri(s"/bank/accounts/$accId")
        }
      }
    }

    "[GET /bank/accounts/:id] fetching account" should {

      val accId = UUID.fromString("5f360fe7-41c2-4325-b0d2-80f2ce220471")

      "return 200 given existent account" in {
        bankAccountQuery.getCurrentBalance _ expects accId returns Future.successful(Option(11.50))

        Get(s"/bank/accounts/$accId") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[Json] shouldBe Json.obj(
            "balance" -> Json.fromDoubleOrNull(11.50)
          )
        }
      }

      "return 404 given non-existent account" in {
        bankAccountQuery.getCurrentBalance _ expects accId returns Future.successful(None)

        Get(s"/bank/accounts/$accId") ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }

    "[POST /bank/accounts/:id] updating account balance" should {

      val accId = UUID.fromString("ebb4e542-3659-4628-8379-52f57a405024")

      "return 200 given amount is positive (deposit)" in {
        val accBalanceUpdateRequest = Json.obj(
          "amount" -> Json.fromDoubleOrNull(5.10)
        )
        val testResult = Post(s"/bank/accounts/$accId", accBalanceUpdateRequest) ~> routes
        val depositMsg = bank.expectMessageType[Deposit]
        depositMsg.accId shouldBe accId
        depositMsg.replyTo ! StatusReply.success(AccountEntity.State(balance = 22.75))

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
        withdrawMsg.accId shouldBe accId
        withdrawMsg.replyTo ! StatusReply.success(AccountEntity.State(balance = 55.08))

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
    }
  }

}
