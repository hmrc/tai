package uk.gov.hmrc.tai.repositories.cache

import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.model.domain.CacheItem

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.SECONDS

@Singleton
class TaiUpdateIncomeMongoRepository @Inject()(mongoConfig: MongoConfig, mongoComponent: MongoComponent)(
  implicit ec: ExecutionContext,
  crypto: Encrypter with Decrypter)
    extends PlayMongoRepository[CacheItem](
      mongoComponent = mongoComponent,
      collectionName = "TaiUpdateIncome",
      domainFormat = CacheItem.encryptedFormat,
      indexes = Seq(
        IndexModel(
          Indexes.ascending("modifiedAt"),
          IndexOptions()
            .name("modified-at-index")
            .expireAfter(mongoConfig.mongoTTLUpdateIncome, SECONDS)
        ),
        IndexModel(
          Indexes.ascending("id"),
          IndexOptions()
            .name("id-index")
            .unique(true)
        )
      )
    )
