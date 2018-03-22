Withdraw Company Car Benefit
----------------------------
  The end point would submit a request to withdraw a company car for provided employment. The user is expected to provide
  the date when the car was withdrawn and an optional fuel withdrawn date. The user's (NPS) version MUST be supplied which
  is used as part of the NPS transaction to ensure NPS data integrity

* **URL**

  `/tai/:nino/tax-account/tax-components/employments/:empId/benefits/company-car/:carId/withdrawn`

* **Method:**

  `PUT`

*  **URL Params**

   **Required:**

   `nino=[Nino]`

   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

   `empId=Int`

   `carId=Int`

* **Payload**

```json
{
  "version" : 10,
  "carWithdrawDate" : "2017-06-24",
  "fuelWithdrawDate" : "2017-06-24"
}
```

* **Success Response:**

  * **Code:** 200 <br />
    **Content:**

```json
{
   "data":"envelopeId",
   "links":[]
}
```

* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-summary/2014"}`

