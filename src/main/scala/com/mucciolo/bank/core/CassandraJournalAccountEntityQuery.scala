package com.mucciolo.bank.core

import akka.actor.typed.ActorSystem
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import cats.implicits.{catsSyntaxOptionId, none}
import cats.syntax.semigroup._
import com.mucciolo.bank.core.Account.EntityTypeKeyName

import scala.concurrent.Future

final class CassandraJournalAccountEntityQuery(implicit system: ActorSystem[_]) extends AccountEntityQuery {

  private val ReadJournal: CassandraReadJournal =
    PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  private def getPersistenceId(accId: Id): String = {
    s"$EntityTypeKeyName|$accId"
  }

  override def getCurrentBalance(accId: Id): Future[Option[BigDecimal]] = {
    ReadJournal.currentEventsByPersistenceId(getPersistenceId(accId), 0, Long.MaxValue)
      .map(_.event)
      .collectType[Account.Event]
      .map {
        case Account.Deposited(amount) => amount.value.some
        case Account.Withdrawn(amount) => (-amount.value).some
      }.runFold(none[BigDecimal])(_ |+| _)
  }

}
