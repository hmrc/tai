package uk.gov.hmrc.tai.controllers.testOnly

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.http.Status.NO_CONTENT
import play.api.test.FakeRequest
import play.api.test.Helpers.status
import uk.gov.hmrc.tai.repositories.JourneyCacheRepository
import uk.gov.hmrc.tai.util.BaseSpec
import play.api.test.Helpers._

import scala.concurrent.Future

class TestOnlyCacheControllerSpec extends BaseSpec {

  private def createSUT(repository: JourneyCacheRepository) =
    new TestOnlyCacheController(repository, loggedInAuthenticationPredicate, cc)

  val fakeRequest = FakeRequest().withHeaders("X-Session-ID" -> "test")

  "TestOnlyCacheController" must {

    "accept and process a DELETE cache instruction *UpdateIncome" in {
      val mockRepository = mock[JourneyCacheRepository]
      when(mockRepository.deleteUpdateIncome(any()))
        .thenReturn(Future.successful())

      val sut = createSUT(mockRepository)
      val result = sut.delete()(FakeRequest("DELETE", "").withHeaders("X-Session-ID" -> "test"))
      status(result) mustBe NO_CONTENT
    }
  }
}
