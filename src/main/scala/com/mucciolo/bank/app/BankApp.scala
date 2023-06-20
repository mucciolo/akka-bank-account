package com.mucciolo.bank.app

import akka.actor.typed.ActorSystem
import com.mucciolo.bank.core.BankEntity
import com.mucciolo.bank.http.HttpServer

import java.util.UUID

object BankApp extends App {

  private val bankId = UUID.fromString("f5a66132-e85d-4f5a-9d4e-635e3a53ed95")
  private implicit val bankSystem: ActorSystem[BankEntity.Command] =
    ActorSystem(BankEntity(bankId), "bank-system")

  HttpServer.runForever(bankSystem)

}
