Add Employment Details
-----------------------
  The end point adds a new employment for the user. The user needs to provide new employment name, start date and payroll number and an optional contact number.

* **URL**

  `/tai/:nino/employments`

* **Method**

  `POST`

*  **URL Params**

   **Required:**

   `nino=[Nino]`

   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

* **Payload**

```json
{
  "employerName" : "dummy employer",
  "startDate" : "2017-06-09",
  "payrollNumber" : "1234",
  "telephoneContactAllowed" : "Yes",
  "telephoneNumber" : "123456789"
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

  OR when a user does not exist

  * **Code:** 404 NOT_FOUND <br />

  OR anything else

  * **Code:** 500 INTERNAL_SERVER_ERROR <br />


