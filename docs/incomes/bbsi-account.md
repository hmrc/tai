Untaxed Interest
----------------
  The end point fetches bank account details a given nino and id
  
* **URL**

  `/tai/:nino/tax-account/income/savings-investments/untaxed-interest/bank-accounts/:id`

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
  "data" : {
    "id" : 1,
    "accountNumber" : "*****5566",
    "sortCode" : "112233",
    "bankName" : "ACCOUNT ONE",
    "grossInterest" : 1500.5,
    "source" : "Customer",
    "numberOfAccountHolders" : 1
  },
  "links" : [ ]
}
```
 
* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-summary/2014"}`

  OR when there is no bank account details found for given id

  * **Code:** 404 NOT_FOUND <br />
  
  OR anything else
  
  * **Code:** 500 INTERNAL_SERVER_ERROR <br />


