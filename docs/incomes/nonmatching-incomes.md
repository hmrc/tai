Matched Incomes
----------------
  The end point returns ceased non-matching employments
  
* **URL**

  `/:nino/employments/year/:year/status/ceased`

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
   "data":{
      "employments":[
         {
            "name":"company name",
            "payrollNumber":"123",
            "startDate":"2016-05-26",
            "endDate":"2016-05-26",
            "annualAccounts":[

            ],
            "taxDistrictNumber":"123",
            "payeNumber":"321",
            "sequenceNumber":2,
            "isPrimary":true,
            "hasPayrolledBenefit":false,
            "receivingOccupationalPension":false
         },
         {
            "name":"company name",
            "payrollNumber":"123",
            "startDate":"2016-05-26",
            "endDate":"2016-05-26",
            "annualAccounts":[

            ],
            "taxDistrictNumber":"1234",
            "payeNumber":"4321",
            "sequenceNumber":3,
            "isPrimary":true,
            "hasPayrolledBenefit":false,
            "receivingOccupationalPension":true
         }
      ]
   }
}
```
 
* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /%nino/employments/year/2018/status/ceased"}`

  OR when there is no tax code incomes are found

  * **Code:** 404 NOT_FOUND <br />
  
  OR anything else
  
  * **Code:** 500 INTERNAL_SERVER_ERROR <br />


