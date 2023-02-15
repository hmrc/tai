package uk.gov.hmrc.tai.connectors

import play.api.Configuration
import uk.gov.hmrc.mongo.cache.MongoCacheRepository
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.util.BaseSpec

class TaiCacheConnectorSpec extends BaseSpec {

  implicit lazy val configuration: Configuration = inject[Configuration]

  "TaiCacheConnector" must {

    lazy val sut = inject[TaiCacheConnector]

    "have the correct collection name" in {
      sut.collectionName mustBe "TAI"
    }

    "use mongoFormats from Cache" in {
      sut.domainFormat mustBe MongoCacheRepository.format
    }

    "have cache default ttl of 15 minutes" in {
      val mongoConfig = new MongoConfig(configuration)
      mongoConfig.mongoTTL mustBe 900
    }
  }

}
