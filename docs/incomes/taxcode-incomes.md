Taxcode Incomes
----------------
  The end point fetches tax code incomes for a given nino and given year
  
* **URL**

  `/tai/:nino/tax-account/:year/income/tax-code-incomes`

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
  "data" : [ {
    "componentType" : "EmploymentIncome",
    "employmentId" : 1,
    "amount" : 1100,
    "description" : "EmploymentIncome",
    "taxCode" : "1150L",
    "name" : "Employer1",
    "basisOperation" : "Week1Month1BasisOperation",
    "status" : "Live",
    "inYearAdjustment" : 0
  }, {
    "componentType" : "EmploymentIncome",
    "employmentId" : 2,
    "amount" : 0,
    "description" : "EmploymentIncome",
    "taxCode" : "1100L",
    "name" : "Employer2",
    "basisOperation" : "OtherBasisOperation",
    "status" : "PotentiallyCeased",
    "inYearAdjustment" : 321.12
  } ],
  "links" : [ ]
}
```
 
* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO/tax-summary/2014"}`

  OR when there is no tax code incomes are found

  * **Code:** 404 NOT_FOUND <br />
  
  OR anything else
  
  * **Code:** 500 INTERNAL_SERVER_ERROR <br />


