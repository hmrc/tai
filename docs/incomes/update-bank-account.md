Update Bank Account
------------------
  The end point submits a request to update bank account interest for a given nino and id. The user expected to provide updated amount
  
* **URL**

  `/tai/:nino/tax-account/income/savings-investments/untaxed-interest/bank-accounts/:id/interest-amount`

* **Method:**
  
  `PUT`
  
*  **URL Params**

   **Required:**
 
   `nino=[Nino]` 
   
   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

   `id=Int`

* **Payload**

```json
{
  "amount" : 10.11
}
```

* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 

```json
{
  "data" : "123456",
  "links" : [ ]
}
```
 
* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-summary/2014"}`

  OR when there is no bank account details found for given id

  * **Code:** 404 NOT_FOUND <br />
    **Content:** `{"statusCode":404,"Error":"Error"}`

  OR anything else

  * **Code:** 500 INTERNAL_SERVER_ERROR <br />

