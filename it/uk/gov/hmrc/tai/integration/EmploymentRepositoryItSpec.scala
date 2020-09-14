package uk.gov.hmrc.tai.integration

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.tai.model.nps2.MongoFormatter
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.EmploymentRepository

import scala.concurrent.ExecutionContext

class EmploymentRepositoryItSpec extends UnitSpec with ScalaFutures with IntegrationPatience  with MongoFormatter with MockitoSugar with GuiceOneAppPerSuite {
  "EmploymentRepository" should {
    "cache multiple years" in {
      lazy val employmentRepository = app.injector.instanceOf[EmploymentRepository]

      def uuid = java.util.UUID.randomUUID.toString

      implicit val hc = HeaderCarrier(sessionId = Some(SessionId(uuid)))
      implicit val ec = app.injector.instanceOf[ExecutionContext]

      val previousTaxYear = TaxYear().prev
      val currentYearEmployment = employmentRepository.employmentsForYear(Nino("CS700100A"), TaxYear()).futureValue
      val previousYearEmployment = employmentRepository.employmentsForYear(Nino("CS700100A"), previousTaxYear).futureValue

      val cachedEmployment = employmentRepository.employmentsForYear(Nino("CS700100A"), TaxYear()).futureValue
      val previousCachedEmployment = employmentRepository.employmentsForYear(Nino("CS700100A"), previousTaxYear).futureValue

      currentYearEmployment shouldBe cachedEmployment
      previousYearEmployment shouldBe previousCachedEmployment
    }
  }
}

