Incomes
----------------
  The end point fetches incomes for a given nino and a given year
  
* **URL**

  `/tai/:nino/tax-account/:year/income`

* **Method:**
  
  `GET`
  
*  **URL Params**

   **Required:**
 
   `nino=[Nino]` 
   
   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

   `year=Int`

* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 

```json
{
  "data" : {
    "taxCodeIncomes" : [ ],
    "nonTaxCodeIncomes" : {
      "otherNonTaxCodeIncomes" : [ {
        "incomeComponentType" : "Profit",
        "amount" : 100,
        "description" : "Profit"
      } ]
    }
  },
  "links" : [ ]
}
```
 
* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-summary/2014"}`
  
  OR anything else
  
  * **Code:** 500 INTERNAL_SERVER_ERROR <br />


