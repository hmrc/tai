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

package uk.gov.hmrc.tai.util

import org.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import uk.gov.hmrc.tai.util.JsonHelper.{OrElseTry, parseType}

import scala.util.{Failure, Try}

class JsonHelperSpec extends PlaySpec with MockitoSugar {

  "orElseTry " must {
    "convert when first reads succeeds" in {
      val reads1: Reads[Int] = implicitly
      val reads2: Reads[Int] = Reads(_ => JsError())

      val combinedReads = reads1 orElseTry reads2
      combinedReads.reads(JsNumber(3)) mustBe JsSuccess(3)
    }

    "convert when second reads succeeds" in {
      val reads1: Reads[Int] = Reads(_ => JsError())
      val reads2: Reads[Int] = implicitly

      val combinedReads = reads1 orElseTry reads2
      combinedReads.reads(JsNumber(3)) mustBe JsSuccess(3)
    }

    "convert when first reads fails with jsresultexception and second reads succeeds" in {
      val reads1: Reads[Int] = Reads(_ => throw JsResultException(Seq(Tuple2(__, Seq()))))
      val reads2: Reads[Int] = implicitly

      val combinedReads = reads1 orElseTry reads2
      combinedReads.reads(JsNumber(3)) mustBe JsSuccess(3)
    }

    "display first errors when both reads fail with json errors" in {
      val reads1: Reads[Int] = Reads(_ => JsError("1"))
      val reads2: Reads[Int] = Reads(_ => JsError("2"))

      val combinedReads = reads1 orElseTry reads2
      combinedReads.reads(JsNumber(3)) mustBe JsError(
        Seq((__, List(JsonValidationError(List("1")))))
      )
    }

    "display first errors when both reads fail with jsresultexceptions" in {
      val exception = JsResultException(Seq(Tuple2(__, Seq(JsonValidationError("1")))))
      val reads1: Reads[Int] = Reads(_ => throw exception)
      val reads2: Reads[Int] = Reads(_ => throw JsResultException(Seq(Tuple2(__, Seq(JsonValidationError("2"))))))

      val combinedReads = reads1 orElseTry reads2
      Try(combinedReads.reads(JsNumber(3))) mustBe Failure(exception)
    }

  }

  "parseType" must {
    "return correct tuple when number present in brackets with leading zeros" in {
      parseType("test (002)") mustBe Some(("test", 2))
    }

    "return correct tuple when number present in brackets without leading zeros" in {
      parseType("test (2)") mustBe Some(("test", 2))
    }

    "return correct tuple when number only present in brackets" in {
      parseType("(2)") mustBe Some(("", 2))
    }

    "return None when no number present" in {
      parseType("test") mustBe None
    }
  }
}
