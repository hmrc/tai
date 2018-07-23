package uk.gov.hmrc.tai.integration

import com.github.tomakehurst.wiremock.client.WireMock.{ok, _}
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.controllers.taxCodeChange.TaxCodeChangeController
import utils.WireMockHelper
import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global

class TaxCodeChangeIntegrationSpec extends PlaySpec with MockitoSugar with WireMockHelper {
  "for a GET for a nino with a tax code change" should {
    "return true" in {
      val testNino = new Generator(new Random).nextNino
      val host = "localhost"
      val port = 9332
      val taxYearLow = 1
      val url = s"http://$host:$port/nps-json-service/nps/itmp/personal-tax-account/tax-code/history/api/v1/$testNino/$taxYearLow"

      server.stubFor(
        get(urlEqualTo(url)).willReturn(ok("hi"))
      )
      
      val x = controller.hasTaxCodeChanged(testNino)

      x map { result =>
        println("*** " + result)
        println(s"result body: ${result.body}")
        result mustBe 500
      }
    }
  }


  implicit val hc = HeaderCarrier()

  private def controller = new TaxCodeChangeController
}
