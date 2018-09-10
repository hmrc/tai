package uk.gov.hmrc.tai.model

import com.google.inject.Inject
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.tai.config.FeatureTogglesConfig


/**
  * grossAmount:1000  THIS IS MANDATORY - MUST BE A POSITIVE WHOLE NUMBER NO GREATER THAN 999999*
  * netAmount:1000    THIS IS OPTIONAL - IF POPULATED MUST BE A POSITIVE WHOLE NUMBER NO GREATER THAN 999999*
  * receiptDate:DD/MM/CCYY  THIS IS OPTIONAL - If populated it Must be in the format dd/mm/ccyy"
  * @param grossAmount
  */
case class IabdUpdateAmount (
                                 employmentSequenceNumber: Int,
                                 grossAmount : Int,
                                 netAmount : Option[Int] = None,
                                 receiptDate : Option[String] = None,
                                 source : Option[Int]=None
                               ) {
  require(grossAmount >= 0, "grossAmount cannot be less than 0")
  require(grossAmount <= 999999, "grossAmount cannot be greater than 999999")
  require(netAmount.forall(_ <= 999999))
}

class IabdUpdateAmountFormats @Inject()(config: FeatureTogglesConfig) {

  def empSeqNoFieldName =
    if (config.desUpdateEnabled) {
      "employmentSeqNo"
    }
    else {
      "employmentSequenceNumber"
    }

  def npsIabdUpdateAmountWrites: Writes[IabdUpdateAmount] = (
    (JsPath \ empSeqNoFieldName).write[Int] and
      (JsPath \ "grossAmount").write[Int] and
      (JsPath \ "netAmount").writeNullable[Int] and
      (JsPath \ "receiptDate").writeNullable[String] and
      (JsPath \ "source").writeNullable[Int]
    )(unlift(IabdUpdateAmount.unapply))

  implicit def formats = Format(Json.reads[IabdUpdateAmount], npsIabdUpdateAmountWrites)

  implicit val formatList = new Writes[List[IabdUpdateAmount]] {
    def writes(updateAmounts: List[IabdUpdateAmount]) : JsValue = {
      Json.toJson(updateAmounts)
    }
  }
}
