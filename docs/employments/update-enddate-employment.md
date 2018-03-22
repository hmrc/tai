Update Employment End Date
--------------------------
  The end point allows the consumer to update the end date for the employment

* **URL**

  `/tai/:nino/employments/:id/end-date`

* **Method**

  `PUT`

*  **URL Params**

   **Required:**

   `nino=[Nino]`

   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

   `id=Int`

* **Payload**

```json
{
  "endDate" : "2017-05-05",
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



