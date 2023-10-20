package com.mucciolo.bank.core

import akka.NotUsed
import akka.stream.scaladsl.Source

trait AccountQuery {
  def getAccountStatement(accId: Id): Source[AccountStatement, NotUsed]
}
