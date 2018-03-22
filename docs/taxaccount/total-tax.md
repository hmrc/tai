Tax Account Total Tax
-------------------
  The end point fetches the total tax values for annual tax account

* **URL**

  `/tai/:nino/tax-account/:year/total-tax`

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
    "amount": 1000,
    "incomeCategories":[ {
        "incomeCategoryType": "UkDividendsIncomeCategory",
        "totalTax": 10,
        "totalTaxableIncome": 20,
        "totalIncome": 30,
        "taxBands":[
          {"bandType": "B",
            "code": "BR",
            "income": 100,
            "tax": 10,
            "rate": 5
          } ]
    } ]
  },
  "links" : [ ]
}
```

* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-account/2014/total-tax"}`

  OR when calculation is not possible

  * **Code:** 400 BAD_REQUEST <br />

  OR when calculation is not possible

  * **Code:** 500 INTERNAL_SERVER_ERROR <br />

