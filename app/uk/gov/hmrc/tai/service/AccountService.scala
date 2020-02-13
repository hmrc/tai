package uk.gov.hmrc.tai.service

import javax.inject.Singleton
import uk.gov.hmrc.tai.model.domain.AnnualAccount
import uk.gov.hmrc.tai.repositories.AnnualAccountRepository

import scala.concurrent.Future

//@Singleton
class AnnualAccountService(
    accountRepository: AnnualAccountRepository
                    ) {

  def annualAccount(): Future[AnnualAccount] = ???



}
