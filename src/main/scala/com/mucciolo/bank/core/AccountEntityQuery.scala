package com.mucciolo.bank.core

import java.util.UUID
import scala.concurrent.Future

trait AccountEntityQuery {
  def getCurrentBalance(accId: UUID): Future[Option[BigDecimal]]
}
