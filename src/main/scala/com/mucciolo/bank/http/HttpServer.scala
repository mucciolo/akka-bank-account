package com.mucciolo.bank.http

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.http.scaladsl.Http
import com.mucciolo.bank.core.{BankEntity, CassandraJournalAccountEntityQuery}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object HttpServer {

  def runForever(implicit bankSystem: ActorSystem[BankEntity.Command]): Unit = {

    val bankAccountQuery = new CassandraJournalAccountEntityQuery()
    val router = new BankRouter(bankSystem, bankAccountQuery)
    val routes = router.routes

    Http()
      .newServerAt("localhost", 8080)
      .bind(routes)
      .onComplete {

        case Success(binding) =>
          val address = binding.localAddress
          bankSystem.log.info(s"Server online at http://${address.getHostString}:${address.getPort}")
          binding.addToCoordinatedShutdown(10.seconds)

        case Failure(ex) =>
          bankSystem.log.error(s"Failed to bind HTTP server. Cause: $ex")
          bankSystem.terminate()

      }(bankSystem.executionContext)
  }

}
