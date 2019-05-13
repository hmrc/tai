Matched Incomes
----------------
  The end point matches tax code incomes to employments
  
* **URL**

  `/:nino/tax-account/year/:year/income/:incomeType/status/:status`

* **Method:**
  
  `GET`
  
*  **URL Params**

   **Required:**
 
   `nino=[Nino]` 
   
   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

   `year=Int`

   `incomeType=[EmploymentIncome|PensionIncome]`

* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 

```json
{
   "data":[
      {
         "taxCodeIncome":{
            "componentType":"EmploymentIncome",
            "employmentId":1,
            "amount":1111,
            "description":"employment",
            "taxCode":"1150L",
            "name":"Employer1",
            "basisOperation":"OtherBasisOperation",
            "status":"Live",
            "inYearAdjustmentIntoCY":0,
            "totalInYearAdjustment":0,
            "inYearAdjustmentIntoCYPlusOne":0
         },
         "employment":{
            "name":"company name",
            "payrollNumber":"888",
            "startDate":"2019-05-26",
            "annualAccounts":[

            ],
            "taxDistrictNumber":"",
            "payeNumber":"",
            "sequenceNumber":1,
            "cessationPay":100,
            "hasPayrolledBenefit":false,
            "receivingOccupationalPension":true
         }
      }
   ]
}
```
> When no record is found it will return an empty body. It will not return a 404
 
 
* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /$NINO/tax-account/year/2018/income/EmploymentIncome/status/Live"}`
  
  OR anything else
  
  * **Code:** 500 INTERNAL_SERVER_ERROR <br />


