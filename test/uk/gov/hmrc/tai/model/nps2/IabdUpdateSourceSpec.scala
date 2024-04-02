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

package uk.gov.hmrc.tai.model.nps2

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.nps2.IabdUpdateSource._

class IabdUpdateSourceSpec extends PlaySpec {
  "IabdUpdateSource" must {
    "return all the IABD update source types" when {
      "set is called" in {
        IabdUpdateSource.set mustBe Set(
          ManualTelephone,
          Letter,
          Email,
          AgentContact,
          OtherForm,
          Internet,
          InformationLetter
        )
      }
    }

    "return an unknown code" when {
      "the code doesn't match an iabd update source code" in {
        IabdUpdateSource(42) mustBe Unknown(42)
      }
    }

    "return the manual telephone IABD update source" when {
      "the code matches the manual telephone IABD update source" in {
        IabdUpdateSource(15) mustBe ManualTelephone
      }
    }

    "return the letter IABD update source" when {
      "the code matches the letter IABD update source" in {
        IabdUpdateSource(16) mustBe Letter
      }
    }

    "return the email IABD update source" when {
      "the code matches the email IABD update source" in {
        IabdUpdateSource(17) mustBe Email
      }
    }

    "return the agent contact IABD update source" when {
      "the code matches the agent contact IABD update source" in {
        IabdUpdateSource(18) mustBe AgentContact
      }
    }

    "return the other form IABD update source" when {
      "the code matches the other form IABD update source" in {
        IabdUpdateSource(24) mustBe OtherForm
      }
    }

    "return the internet IABD update source" when {
      "the code matches the internet IABD update source" in {
        IabdUpdateSource(39) mustBe Internet
      }
    }

    "return the information letter IABD update source" when {
      "the code matches the information letter IABD update source" in {
        IabdUpdateSource(40) mustBe InformationLetter
      }
    }
  }
}
