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

package uk.gov.hmrc.tai.integration.utils

import org.apache.pekko.Done
import com.google.inject.Inject
import play.api.cache.AsyncCacheApi

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

class FakeAsyncCacheApi @Inject()() extends AsyncCacheApi {

  def set(key: String, value: Any, expiration: Duration): Future[Done] = Future.successful(Done)

  def remove(key: String): Future[Done] = Future.successful(Done)

  def get[T: ClassTag](key: String): Future[Option[T]] = Future.successful(None)

  def getOrElseUpdate[A: ClassTag](key: String, expiration: Duration)(orElse: => Future[A]): Future[A] = orElse

  def removeAll(): Future[Done] = Future.successful(Done)

}