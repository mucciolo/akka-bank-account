package com.mucciolo.bank.core

import java.util.UUID
import scala.concurrent.Future

// TODO refactor to get transactions history
trait AccountEntityQuery {
  def getCurrentBalance(accId: UUID): Future[Option[BigDecimal]]
}
