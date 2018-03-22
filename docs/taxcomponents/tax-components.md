Coding Components
-----------------
  The end point provides a list of coding components

* **URL**

  `/tai/:nino/tax-account/:year/tax-components`

* **Method:**

  `GET`

*  **URL Params**

   **Required:**

   `nino=[Nino]`

   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

   `year=Int`

* **Success Response:**

  * **Code:** 200 <br />
    **Content:**

```json
{
  "data" : [ {
    "componentType" : "EmployerProvidedServices",
    "employmentId" : 12,
    "amount" : 12321,
    "description" : "Some Description",
    "iabdCategory" : "Benefit"
  }, {
    "componentType" : "PersonalPensionPayments",
    "employmentId" : 31,
    "amount" : 12345,
    "description" : "Some Description Some",
    "iabdCategory" : "Allowance"
  } ],
  "links" : [ ]
}
```

* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-summary/2014"}`

  OR when no coding components are available

  * **Code:** 404 NOT_FOUND <br />

