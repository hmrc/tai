Remove Company Benefit
----------------------------
The end point would submit a request to remove a company benefit.

* **URL**

  `/tai/:nino/tax-account/tax-component/employments/:employmentId/benefits/ended-benefit`

* **Method:**

  `POST`

*  **URL Params**

   **Required:**

   `nino=[Nino]`

   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

   `employmentId=Int`

* **Payload**

```json
{
  "benefitType": "Accommodation",
  "stopDate": "2024-12-23",
  "valueOfBenefit": "1200",
  "contactByPhone": "Yes",
  "phoneNumber": "1234567890"
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
      **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-account/tax-component/employments/$employmentId/benefits/ended-benefit"}`

