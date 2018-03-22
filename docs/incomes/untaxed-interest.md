Untaxed Interest
----------------
  The end point fetches non taxed interest for a given nino
  
* **URL**

  `/tai/:nino/tax-account/income/savings-investments/untaxed-interest`

* **Method:**
  
  `GET`
  
*  **URL Params**

   **Required:**
 
   `nino=[Nino]` 
   
   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 

```json
{
  "data" : {
    "incomeComponentType" : "UntaxedInterestIncome",
    "amount" : 123,
    "description" : "Untaxed Interest",
    "bankAccounts" : [ ]
  },
  "links" : [ ]
}
```
 
* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-summary/2014"}`

  OR when there is no untaxed interest

  * **Code:** 404 NOT_FOUND <br />
  
  OR anything else
  
  * **Code:** 500 INTERNAL_SERVER_ERROR <br />


