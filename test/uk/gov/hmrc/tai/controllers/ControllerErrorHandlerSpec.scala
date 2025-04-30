/*
 * Copyright 2025 HM Revenue & Customs
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

///*
// * Copyright 2023 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.tai.controllers
//
//import play.api.test.Helpers.{status, *}
//import uk.gov.hmrc.http.{BadGatewayException, BadRequestException, GatewayTimeoutException, HttpException, InternalServerException, NotFoundException}
//import uk.gov.hmrc.tai.util.{BaseSpec, NpsExceptions}
//
//class ControllerErrorHandlerSpec extends BaseSpec with NpsExceptions {
//
//  "ControllerErrorHandler" must {
//    "return BAD_REQUEST" when {
//      "there is hod BAD_REQUEST exception" in {
//        val sut = createSUT
//        val pf = sut.taxAccountErrorHandler()
//        val result = pf(new BadRequestException(CodingCalculationCYPlusOne))
//        status(result) mustBe BAD_REQUEST
//      }
//    }
//    "return NOT_FOUND" when {
//      "tax account returns NOT_FOUND" in {
//        val sut = createSUT
//        val pf = sut.taxAccountErrorHandler()
//        val result = pf(new NotFoundException("No coding components found"))
//        status(result) mustBe NOT_FOUND
//      }
//    }
//    "return internal server error" when {
//      "tax account returns internal server error" in {
//        val sut = createSUT
//        val pf = sut.taxAccountErrorHandler()
//        val result = the[InternalServerException] thrownBy
//          pf(new InternalServerException("any other error")).futureValue
//        result.getMessage mustBe "any other error"
//      }
//    }
//
//    "return Bad Gateway" when {
//      "tax account returns GatewayTimeoutException exception" in {
//        val sut = createSUT
//        val pf = sut.taxAccountErrorHandler()
//        val result = pf(new GatewayTimeoutException("any other error"))
//        status(result) mustBe BAD_GATEWAY
//      }
//
//      "tax account returns BadGatewayException" in {
//        val sut = createSUT
//        val pf = sut.taxAccountErrorHandler()
//        val result = pf(new BadGatewayException("any other error"))
//        status(result) mustBe BAD_GATEWAY
//      }
//
//      "tax account returns HttpException exception with message 502 Bad Gateway" in {
//        val sut = createSUT
//        val pf = sut.taxAccountErrorHandler()
//        val result = pf(new HttpException("error containing 502 Bad Gateway", 500))
//        status(result) mustBe BAD_GATEWAY
//      }
//
//      "tax account returns HttpException exception without message 502 Bad Gateway" in {
//        val sut = createSUT
//        val pf = sut.taxAccountErrorHandler()
//        val result = the[HttpException] thrownBy
//          pf(new HttpException("any other error", 500)).futureValue
//        result.getMessage mustBe "any other error"
//      }
//    }
//  }
//
//  def createSUT = new SUT
//  class SUT extends ControllerErrorHandler
//}
