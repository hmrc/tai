Tax Account Summary
-------------------
  The end point fetches annual tax account summary

* **URL**

  `/tai/:nino/tax-account/:year/summary`

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
    "totalEstimatedTax" : 2222,
    "taxFreeAmount" : 1,
    "totalInYearAdjustment" : 56.78
  },
  "links" : [ ]
}
```

* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-summary/2014"}`

  OR when calculation is not possible

  * **Code:** 400 BAD_REQUEST <br />

  OR when calculation is not possible

  * **Code:** 500 INTERNAL_SERVER_ERROR <br />

