Incomes
----------------
  The end point updates the estimated pay for the specified nino, tax year and employment
  
* **URL**

  `/tai/:nino/tax-account/snapshots/:snapshotId/incomes/tax-code-incomes/:employmentId/estimated-pay`

* **Method:**
  
  `PUT`
  
*  **URL Params**

   **Required:**
 
   `nino=[Nino]` 
   
   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

   `snapshotId=Int`

   `employmentId=Int`

* **Payload**

```json
{
  "amount" : 0
}
```

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

   OR invalid amount

    * **Code:** 400 BAD_REQUEST <br />

  OR anything else
  
  * **Code:** 500 INTERNAL_SERVER_ERROR <br />


