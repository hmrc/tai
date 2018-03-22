Benefits for Tax year
---------------------
  The end point provides the benefits for the provided nino and tax year

* **URL**

  `/tai/:nino/tax-account/:year/benefits`

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
  "data" : {
    "companyCarBenefits" : [ {
      "employmentSeqNo" : 12,
      "grossAmount" : 200,
      "companyCars" : [ {
        "carSeqNo" : 10,
        "makeModel" : "a car",
        "hasActiveFuelBenefit" : false,
        "dateMadeAvailable" : "2014-06-09"
      } ],
      "version" : 123
    }, {
      "employmentSeqNo" : 0,
      "grossAmount" : 800,
      "companyCars" : [ ]
    } ],
    "otherBenefits" : [ {
      "benefitType" : "Accommodation",
      "employmentId" : 126,
      "amount" : 111
    }, {
      "benefitType" : "Assets",
      "amount" : 222
    } ]
  },
  "links" : [ ]
}
```

* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-summary/2014"}`

