/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.tai.controllers

import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.http.{InternalServerException, BadRequestException,NotFoundException}
import uk.gov.hmrc.tai.util.NpsExceptions

import scala.concurrent.Await
import scala.concurrent.duration._

class ControllerErrorHandlerSpec extends PlaySpec with FakeTaiPlayApplication
  with MockitoSugar
  with NpsExceptions{

  "ControllerErrorHandler"should{
    "return BAD_REQUEST"when {
      "there is hod BAD_REQUEST exception"in {
        val sut = createSUT
        val pf = sut.taxAccountErrorHandler()(FakeRequest())
        val result = pf(new BadRequestException(CodingCalculationCYPlusOne))
        status(result) mustBe BAD_REQUEST
      }
    }
    "return NOT_FOUND" when{
      "tax account returns NOT_FOUND"in{
        val sut = createSUT
        val pf = sut.taxAccountErrorHandler()(FakeRequest())
        val result = pf(new NotFoundException("No coding components found"))
        status(result) mustBe NOT_FOUND
      }
    }
    "return internal server error" when{
      "tax account returns internal server error"in{
        val sut = createSUT
        val pf = sut.taxAccountErrorHandler()(FakeRequest())
        val result = the[InternalServerException] thrownBy Await.result(
          pf(new InternalServerException("any other error")), 5.seconds
        )
        result.getMessage mustBe "any other error"
      }
    }
  }

  def createSUT = new SUT
  class SUT extends ControllerErrorHandler
}