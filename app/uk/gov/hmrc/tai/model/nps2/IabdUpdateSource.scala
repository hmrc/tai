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

sealed trait IabdUpdateSource {
  def code: Int
}

object IabdUpdateSource {
  object ManualTelephone extends IabdUpdateSource { val code = 15 }
  object Letter extends IabdUpdateSource { val code = 16 }
  object Email extends IabdUpdateSource { val code = 17 }
  object AgentContact extends IabdUpdateSource { val code = 18 }
  object OtherForm extends IabdUpdateSource { val code = 24 }
  object Internet extends IabdUpdateSource { val code = 39 }
  object InformationLetter extends IabdUpdateSource { val code = 40 }
  case class Unknown(code: Int) extends IabdUpdateSource

  val set: Set[IabdUpdateSource] =
    Set(ManualTelephone, Letter, Email, AgentContact, OtherForm, Internet, InformationLetter)

  def apply(i: Int): IabdUpdateSource = set.find(_.code == i).getOrElse(Unknown(i))
}
