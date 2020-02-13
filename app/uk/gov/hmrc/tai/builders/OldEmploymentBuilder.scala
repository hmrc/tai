package uk.gov.hmrc.tai.builders

import play.api.Logger
import uk.gov.hmrc.tai.model.domain
import uk.gov.hmrc.tai.model.domain.{AnnualAccount, Employment, OldEmployment}

object OldEmploymentBuilder {

  def build(employments: Seq[Employment], accounts: Seq[AnnualAccount]): Seq[OldEmployment] = {

    val accountAssignedEmployments = accounts.flatMap(account => {

      employments.filter(emp => emp.employerDesignation == account.employerDesignation) match {
        case Seq(single) if(single.key == account.key) => Some(OldEmployment(account, single))
        case many => Logger.warn(s"multiple matches found")
          many.find(e => e.key == account.key).map(e => OldEmployment(account, e))
        case _ =>
          Logger.warn(s"no match found")
          None
        //TODO: restore auditing.
      }
    })
    
  }
}
