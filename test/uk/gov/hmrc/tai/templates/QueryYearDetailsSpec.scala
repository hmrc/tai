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

package uk.gov.hmrc.tai.templates

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatestplus.play.PlaySpec
import play.twirl.api.Html

class QueryYearDetailsSpec extends PlaySpec {

  "Query Year Details" must {
    "display headings" in {
      doc.select("h2").text() mustBe "Internal Information"
      doc.select("h3").get(0).text() mustBe "Summary"
      doc.select("h3").get(1).text() mustBe "Tell us what tax year your query is about"
    }

    "display the query year" in {
      val tableRow = doc.select("table:nth-of-type(1) > tbody > tr")

      tableRow.select("td:nth-of-type(1)").text() mustBe "Which tax year is your query about?"
      tableRow.select("td:nth-of-type(2)").text() mustBe displayableTaxYearRange
    }
  }

  private val displayableTaxYearRange = "10 October 2017 to 11 October 2017"
  val queryYearDetailsView: Html = uk.gov.hmrc.tai.templates.html.QueryYearDetails(displayableTaxYearRange)
  val doc: Document = Jsoup.parse(queryYearDetailsView.toString())
}
