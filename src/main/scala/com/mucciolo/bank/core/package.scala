package com.mucciolo.bank

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

import java.time.Instant
import java.util.UUID

package object core {

  type Id = UUID
  type PositiveAmount = BigDecimal Refined Positive

  final case class AccountStatementEntry(timestamp: Instant, amount: BigDecimal)
  final case class AccountStatement(entries: List[AccountStatementEntry])

}
