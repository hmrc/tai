Add Pension Provider Details
----------------------------
  The end point adds a new pension provider for the user. The user needs to provide new pension provider name, start date and pension number and an optional contact number.

* **URL**

  `/tai/:nino/pensionProvider`

* **Method**

  `POST`

*  **URL Params**

   **Required:**

   `nino=[Nino]`

   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

* **Payload**

```json
{
  "pensionProviderName" : "dummy pension provider",
  "startDate" : "2017-06-09",
  "pensionNumber" : "1234",
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


