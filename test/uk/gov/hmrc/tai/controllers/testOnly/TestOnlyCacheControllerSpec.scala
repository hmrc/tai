/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.tai.controllers.testOnly

import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.any
import play.api.http.Status.NO_CONTENT
import play.api.test.FakeRequest
import play.api.test.Helpers.status
import uk.gov.hmrc.tai.util.BaseSpec
import play.api.test.Helpers._
import uk.gov.hmrc.tai.repositories.deprecated.JourneyCacheRepository

import scala.concurrent.Future

class TestOnlyCacheControllerSpec extends BaseSpec {

  private def createSUT(repository: JourneyCacheRepository) =
    new TestOnlyCacheController(repository, loggedInAuthenticationAuthJourney, cc)

  val fakeRequest = FakeRequest().withHeaders("X-Session-ID" -> "test")

  "TestOnlyCacheController" must {

    "accept and process a DELETE cache instruction *UpdateIncome" in {
      val mockRepository = mock[JourneyCacheRepository]
      when(mockRepository.deleteUpdateIncome(any()))
        .thenReturn(Future.successful(Done))

      val sut = createSUT(mockRepository)
      val result = sut.delete()(FakeRequest("DELETE", "").withHeaders("X-Session-ID" -> "test"))
      status(result) mustBe NO_CONTENT
    }
  }
}
