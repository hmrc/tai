package uk.gov.hmrc.tai.integration

import uk.gov.hmrc.play.it._


class TaiEmbeddedServer (override val testName: String) extends MicroServiceEmbeddedServer {
  override protected lazy val externalServices: Seq[ExternalService] = Seq()
  override def additionalConfig = Map(
    "cache.expiryInMinutes" -> 1
  )
}


class TaiBaseSpec (testName: String) extends ServiceSpec {
  override protected val server: ResourceProvider with StartAndStopServer = new TaiEmbeddedServer(testName)
}