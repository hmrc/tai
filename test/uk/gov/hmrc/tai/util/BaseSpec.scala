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
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.cache.AsyncCacheApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Injecting
import play.api.inject.bind
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainBytes, PlainContent, PlainText}
import uk.gov.hmrc.tai.controllers.FakeTaiPlayApplication
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate

import scala.concurrent.ExecutionContext

trait BaseSpec
    extends PlaySpec with MockitoSugar with MockAuthenticationPredicate with FakeTaiPlayApplication with ScalaFutures
    with Injecting {
//  implicit val fakeEncrypterDecrypter: Encrypter with Decrypter = new Encrypter with Decrypter {
//    override def encrypt(plain: PlainContent): Crypted = Crypted(plain.toString)
//
//    override def decrypt(reversiblyEncrypted: Crypted): PlainText = PlainText(reversiblyEncrypted.value)
//
//    override def decryptAsBytes(reversiblyEncrypted: Crypted): PlainBytes = PlainBytes(
//      reversiblyEncrypted.value.getBytes
//    )
//  }
  implicit lazy val ec: ExecutionContext = inject[ExecutionContext]
  val responseBody: String = ""

  lazy val fakeAsyncCacheApi = new FakeAsyncCacheApi()

  override implicit lazy val app: Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[AsyncCacheApi].toInstance(fakeAsyncCacheApi)
      )
      .build()

}
