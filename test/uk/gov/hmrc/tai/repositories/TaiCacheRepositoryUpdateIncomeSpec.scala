package uk.gov.hmrc.tai.repositories

import play.api.Configuration
import uk.gov.hmrc.mongo.cache.MongoCacheRepository
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.connectors.TaiUpdateIncomeCacheRepository
import uk.gov.hmrc.tai.util.BaseSpec

class TaiCacheRepositoryUpdateIncomeSpec extends BaseSpec {

  implicit lazy val configuration: Configuration = inject[Configuration]

  "TaiCacheRepositoryUpdateIncome" must {

    lazy val sut: TaiUpdateIncomeCacheRepository = inject[TaiUpdateIncomeCacheRepository]

    "have the correct collection name" in {
      sut.collectionName mustBe "TaiUpdateIncome"
    }

    "use mongoFormats from Cache" in {
      sut.domainFormat mustBe MongoCacheRepository.format
    }
    "have cache default ttl of 2 days" in {
      val mongoConfig = new MongoConfig(configuration)
      mongoConfig.mongoTTLUpdateIncome mustBe 172800
    }
  }
}
