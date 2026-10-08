/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.tai.repositories

import com.google.inject.{Inject, Singleton}
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.tai.config.UpscanConfig
import uk.gov.hmrc.tai.model.dms.DmsSubmissionMetadata
import uk.gov.hmrc.tai.model.upscan.{UpscanSubmission, UpscanUploadedFile}

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpscanSubmissionRepository @Inject() (mongoComponent: MongoComponent, config: UpscanConfig)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository[UpscanSubmission](
      collectionName = "upscanSubmissions",
      mongoComponent = mongoComponent,
      domainFormat = UpscanSubmission.format,
      indexes = Seq(
        IndexModel(
          Indexes.ascending("createdAt"),
          IndexOptions()
            .name("upscanSubmissionsExpiry")
            .expireAfter(config.submissionTTLSeconds, TimeUnit.SECONDS)
        ),
        IndexModel(
          Indexes.ascending("submissionId"),
          IndexOptions().name("submissionIdIdx").unique(true)
        ),
        IndexModel(
          Indexes.ascending("files.reference"),
          IndexOptions().name("fileReferenceIdx")
        )
      ),
      replaceIndexes = true
    ) {

  def create(
    submissionId: String,
    files: Seq[UpscanUploadedFile] = Seq.empty,
    dmsMetadata: Option[DmsSubmissionMetadata] = None
  ): Future[UpscanSubmission] = {
    val submission = UpscanSubmission(submissionId, files, dmsMetadata)
    collection.insertOne(submission).toFuture().map(_ => submission)
  }

  private def findBySubmissionId(submissionId: String): Future[Option[UpscanSubmission]] =
    collection.find(equal("submissionId", submissionId)).headOption()

  private def findByFileReference(reference: String): Future[Option[UpscanSubmission]] =
    collection.find(equal("files.reference", reference)).headOption()

  def addFile(submissionId: String, file: UpscanUploadedFile): Future[Option[UpscanSubmission]] =
    findBySubmissionId(submissionId).flatMap {
      case Some(submission) =>
        val updated = submission.copy(files = submission.files :+ file)
        collection.replaceOne(equal("submissionId", submissionId), updated).toFuture().map(_ => Some(updated))
      case None => Future.successful(None)
    }

  def updateFileStatus(
    reference: String,
    status: String,
    downloadUrl: Option[String] = None,
    failureReason: Option[String] = None
  ): Future[Option[UpscanSubmission]] =
    findByFileReference(reference).flatMap {
      case Some(submission) =>
        val updatedFiles = submission.files.map { file =>
          if (file.reference == reference) {
            file.copy(
              status = status,
              downloadUrl = downloadUrl.orElse(file.downloadUrl),
              failureReason = failureReason.orElse(file.failureReason)
            )
          } else {
            file
          }
        }
        val updated = submission.copy(files = updatedFiles)
        collection
          .replaceOne(equal("submissionId", submission.submissionId), updated)
          .toFuture()
          .map(_ => Some(updated))
      case None => Future.successful(None)
    }
}
