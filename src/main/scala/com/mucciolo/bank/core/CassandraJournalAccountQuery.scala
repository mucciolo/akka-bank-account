package com.mucciolo.bank.core

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.scaladsl._
import cats.implicits.{catsSyntaxOptionId, none}
import com.mucciolo.bank.core.Account.EntityTypeKeyName

final class CassandraJournalAccountQuery(implicit system: ActorSystem[_]) extends AccountQuery {

  private val ReadJournal: CassandraReadJournal =
    PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  private def getPersistenceId(accId: Id): String = {
    s"$EntityTypeKeyName|$accId"
  }

  override def getAccountStatement(accId: Id): Source[AccountStatement, NotUsed] = {
    ReadJournal.currentEventsByPersistenceId(getPersistenceId(accId), 0, Long.MaxValue)
      .map(_.event)
      .collectType[Account.Event]
      .map {
        case Account.Deposited(timestamp, amount) => AccountStatementEntry(timestamp, amount.value)
        case Account.Withdrawn(timestamp, amount) => AccountStatementEntry(timestamp, -amount.value)
      }
      .fold(List.newBuilder[AccountStatementEntry]) { (builder, entry) => builder.addOne(entry) }
      .map(_.result())
      .map(AccountStatement)
  }

}
