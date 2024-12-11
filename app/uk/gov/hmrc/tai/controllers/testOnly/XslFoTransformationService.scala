/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.tai.controllers.testOnly

import com.google.inject.{ImplementedBy, Inject}
import org.apache.commons.io.output.ByteArrayOutputStream
import org.apache.fop.apps.{FOUserAgent, FopConfParser, FopFactory, FopFactoryBuilder}
import org.apache.xmlgraphics.util.MimeConstants
import play.api.Environment

import java.io.{File, StringReader}
import javax.xml.transform.sax.SAXResult
import javax.xml.transform.stream.StreamSource
import javax.xml.transform.{Transformer, TransformerFactory}
import scala.util.Using

@ImplementedBy(classOf[XslFoTransformationServiceFopImpl])
trait XslFoTransformationService {
  def toPdfBytes(xslFoDocument: String): Array[Byte]
}

class XslFoTransformationServiceFopImpl @Inject() (
  environment: Environment
) extends XslFoTransformationService {

  def toPdfBytes(xslFoDocument: String): Array[Byte] = {

    val source = createSource(xslFoDocument)
    val transformer = createTransformer()

    Using.resource(new ByteArrayOutputStream()) { out =>
      transformer.transform(source, createTransformation(out))
      out.toByteArray
    }
  }

  private def createSource(input: String): StreamSource =
    new StreamSource(new StringReader(input))

  private def createTransformer(): Transformer =
    TransformerFactory
      .newInstance()
      .newTransformer()

  private def createTransformation(out: ByteArrayOutputStream): SAXResult = {

    val fopConfigFilePath: String = s"/conf/pdf/fop.xconf"
    val xconf: File = environment.getFile(fopConfigFilePath)
    val parser: FopConfParser = new FopConfParser(xconf)
    val builder: FopFactoryBuilder = parser.getFopFactoryBuilder
    val fopFactory: FopFactory = builder.build()

    val userAgent: FOUserAgent = fopFactory.newFOUserAgent()
    userAgent.setAccessibility(true)

    val fop = fopFactory.newFop(MimeConstants.MIME_PDF, userAgent, out)
    new SAXResult(fop.getDefaultHandler)
  }
}
