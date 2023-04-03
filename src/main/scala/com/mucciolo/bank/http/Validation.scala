package com.mucciolo.bank.http

import cats.data.{Validated, ValidatedNec}
import cats.implicits._

object Validation {

  trait Required[A] extends (A => Boolean)
  trait Nonzero[A] extends (A => Boolean)

  implicit val requiredBigDec: Required[BigDecimal] = _ != null
  implicit val nonZeroBigDec: Nonzero[BigDecimal] = _ != 0

  private def required[A](value: A)(implicit req: Required[A]): Boolean = req(value)
  private def nonZero[A](value: A)(implicit nonzero: Nonzero[A]): Boolean = nonzero(value)

  type ValidationResult[A] = ValidatedNec[ValidationFailure, A]

  trait ValidationFailure {
    def errorMessage: String
  }

  final case class MissingField(fieldName: String) extends ValidationFailure {
    override def errorMessage = s"Field '$fieldName' is missing'"
  }

  final case class ZeroValue(fieldName: String) extends ValidationFailure {
    override def errorMessage = s"Field '$fieldName' is zero"
  }

  def validateRequired[A: Required](value: A, fieldName: String): ValidationResult[A] =
    Validated.condNec(required(value), value, MissingField(fieldName))

  def validateNonzero[A: Nonzero](value: A, fieldName: String): ValidationResult[A] =
    Validated.condNec(nonZero(value), value, ZeroValue(fieldName))

  trait Validator[A] {
    def validate(value: A): ValidationResult[A]
  }

  def validate[A](value: A)(implicit validator: Validator[A]): ValidationResult[A] =
    validator.validate(value)
}
