Employment Company Car Benefit
------------------------------
  The end point fetches the benefits for the provided nino and employment

* **URL**

  `/tai/:nino/tax-account/tax-components/employments/:id/benefits/company-car`

* **Method:**

  `GET`

*  **URL Params**

   **Required:**

   `nino=[Nino]`

   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

   `id=Int`

* **Success Response:**

  * **Code:** 200 <br />
    **Content:**

```json
{
  "data" : {
    "employmentSeqNo" : 10,
    "grossAmount" : 1000,
    "companyCars" : [ {
      "carSeqNo" : 10,
      "makeModel" : "a car",
      "hasActiveFuelBenefit" : false,
      "dateMadeAvailable" : "2014-06-09"
    } ],
    "version" : 1
  },
  "links" : [ ]
}
```

* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-summary/2014"}`

