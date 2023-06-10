package com.mucciolo.bank

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

import java.util.UUID

package object core {
  type Id = UUID
  type PositiveAmount = BigDecimal Refined Positive
}
