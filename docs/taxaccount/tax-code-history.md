Tax Code History
-------------------
  The end point returns details of the current and previous operated tax codes for a given nino

* **URL**

  `/tai/:nino/tax-account/tax-code-history`

* **Method:**

  `GET`

* **Success Response:**

  * **Code:** 200 <br />
    **Content:**

```json
{
  "data" : {
    "current": {
      "taxCode": "830L",
      "startDate": "2018-06-27",
      "endDate": "2019-04-05",
      "employerName": "Employer Name"
    },
    "previous": {
      "taxCode": "1150L",
      "startDate": "2018-04-06",
      "endDate": "2018-06-26",
      "employerName": "Employer Name"
    }
  },
  "links" : [ ]
}
```

* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-account/tax-code-history"}`