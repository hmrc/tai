Employment by Id
----------------
  Retrieves employments by provided id

* **URL**

  `/:nino/employments/:id`

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
   "data":{
      "name":"company name",
      "payrollNumber":"888",
      "startDate":"2017-06-09",
      "annualAccounts":[],
      "taxDistrictNumber":"",
      "payeNumber":"",
      "sequenceNumber":2
   },
   "links":[]
}
```

* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-summary/2014"}`

  OR when an employment does not exist

  * **Code:** 404 NOT_FOUND <br />

  OR when a employment contains stub annual account data due to RTI being unavailable

  * **Code:** 502 BAD_GATEWAY <br />

  OR anything else

  * **Code:** 500 INTERNAL_SERVER_ERROR <br />


