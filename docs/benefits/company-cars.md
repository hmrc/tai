Company Cars
------------
  The end point fetches all the company car benefits for nino

* **URL**

  `/tai/:nino/tax-account/tax-components/benefits/company-cars`

* **Method:**

  `GET`

*  **URL Params**

   **Required:**

   `nino=[Nino]`

   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

* **Success Response:**

  * **Code:** 200 <br />
    **Content:**

```json
{
  "data" : {
    "companyCarBenefits" : [ {
      "employmentSeqNo" : 10,
      "grossAmount" : 1000,
      "companyCars" : [ {
        "carSeqNo" : 10,
        "makeModel" : "a car",
        "hasActiveFuelBenefit" : false,
        "dateMadeAvailable" : "2014-06-10"
      } ],
      "version" : 1
    } ]
  },
  "links" : [ ]
}
```

* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-summary/2014"}`

  OR when company car does not exist

  * **Code:** 404 NOT_FOUND <br />

