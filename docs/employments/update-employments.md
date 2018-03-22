Update Employments Details
--------------------------
  The end point updates the incorrect employment details for a provided year. User must provide information about what they want to update,
  along with optional phone number

* **URL**

  `/tai/:nino/employments/years/:year/update`

* **Method**

  `POST`

*  **URL Params**

   **Required:**

   `nino=[Nino]`

   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

   `year=Int`

* **Payload**

```json
{
   "whatYouToldUs":"updated details",
   "telephoneContactAllowed":"Yes",
   "telephoneNumber":"123123"
}
```

* **Success Response:**

  * **Code:** 200 <br />
    **Content:**

```json
{
   "data":"envelopeId",
   "links":[

   ]
}
```

* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-summary/2014"}`

  OR when a user does not exist

  * **Code:** 404 NOT_FOUND <br />

  OR anything else

  * **Code:** 500 INTERNAL_SERVER_ERROR <br />


