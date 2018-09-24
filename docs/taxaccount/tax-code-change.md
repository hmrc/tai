Tax Code Change
-------------------
  The end point returns details of the current and previous operated tax codes for a given nino

* **URL**

  `/tai/:nino/tax-account/tax-code-change`

* **Method:**

  `GET`

* **Success Response:**

  * **Code:** 200 <br />
    **Content:**

```json
{
  "data" : {
    "current": [{
      "taxCode": "830L",
      "taxCodeId": 1,
      "employerName": "Employer Name",
      "operatedTaxCode": true,
      "p2Issued": true,
      "startDate": "2018-06-27",
      "endDate": "2019-04-05",
      "payrollNumber": "1",
      "pensionIndicator": true,
      "primary": true
    }],
    "previous": [{
      "taxCode": "1150L",
      "taxCodeId": 2,
      "employerName": "Employer Name",
      "operatedTaxCode": true,
      "p2Issued": true,
      "startDate": "2018-04-06",
      "endDate": "2018-06-26",
      "payrollNumber": "1",
      "pensionIndicator": true,
      "primary": true
    }]
  },
  "links" : [ ]
}
```

Note:
- "payrollNumber" is an optional field

* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-account/tax-code-change"}`