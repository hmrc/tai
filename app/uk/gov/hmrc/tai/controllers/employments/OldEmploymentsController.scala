package uk.gov.hmrc.tai.controllers.employments
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import com.google.inject.Inject
import javax.inject.Singleton
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{BadRequestException, NotFoundException}
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.api.{ApiFormats, ApiResponse, EmploymentCollection, OldEmploymentCollection}
import uk.gov.hmrc.tai.model.domain.{Employment, EndEmployment}
import uk.gov.hmrc.tai.model.error.{EmploymentAccountStubbed, EmploymentNotFound}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.EmploymentService

//TODO: Renamme - this controller can work for both Old and New CombinedEmploymentAccount information formats. Maybe even so far as to remove the OldEmploymentsModel and replacing it with a Contract case class.
@Singleton
class OldEmploymentsController @Inject()(employmentService: EmploymentService, authentication: AuthenticationPredicate)
  extends BaseController with ApiFormats{

  def employments(nino: Nino, year: TaxYear): Action[AnyContent] = authentication.async {

        //TODO: When switch on, return 503 with error message.
    implicit request =>
      employmentService.employments(nino, year)
        .map { employments: Seq[Employment] =>
          //TODO: Need to perform the model choice and merge here.

          Ok(Json.toJson(ApiResponse(OldEmploymentCollection(employments), Nil)))
        }
    .recover {
      case ex: NotFoundException   => NotFound(ex.getMessage)
      case ex: BadRequestException => BadRequest(ex.getMessage)
      case ex                      => InternalServerError(ex.getMessage)
    }
  }

  def employment(nino: Nino, id: Int): Action[AnyContent] = authentication.async { implicit request =>
    //TODO: When switch on, return 503 with error message.
    employmentService
      .employment(nino, id)
      .map {
        case Right(employment) => Ok(Json.toJson(ApiResponse(employment, Nil))) //TODO: Weld to Account Info
        case Left(EmploymentNotFound) => NotFound("EmploymentNotFound")
        case Left(EmploymentAccountStubbed) => BadGateway("Employment contains stub annual account data due to RTI unavailability") //TODO: Can we remove this?
      }
      .recover {
        case _: NotFoundException => NotFound("Employment not found")
        case error                => InternalServerError(error.getMessage)
      }
  }
}
