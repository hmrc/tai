Annual Account Employments
----
  Retrieves all employments for a given year with Annual Account information
  
* **URL**

  `/tai/:nino/employments/years/:year`

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
            "payrollNumber":"111",
            "startDate":"2005-11-07",
            "annualAccounts":[
               {
                  "key":"111-A11-000111",
                  "taxYear":2016,
                  "realTimeStatus":"Available",
                  "payments":[
                     {
                        "date":"2015-04-30",
                        "amountYearToDate":20000,
                        "taxAmountYearToDate":1880,
                        "nationalInsuranceAmountYearToDate":110.03,
                        "amount":102.02,
                        "taxAmount":111.02,
                        "nationalInsuranceAmount":109.03,
                        "payFrequency":"OneOff"
                     },
                     {
                        "date":"2015-07-31",
                        "amountYearToDate":20000,
                        "taxAmountYearToDate":1880,
                        "nationalInsuranceAmountYearToDate":110.03,
                        "amount":102.02,
                        "taxAmount":111.02,
                        "nationalInsuranceAmount":109.03,
                        "payFrequency":"OneOff"
                     }
                  ],
                  "endOfTaxYearUpdates":[
                     {
                        "date":"2016-06-01",
                        "adjustments":[
                           {
                              "type":"TaxAdjustment",
                              "amount":-10.99
                           },
                           {
                              "type":"NationalInsuranceAdjustment",
                              "amount":-5.99
                           }
                        ]
                     }
                  ]
               },
               {
                  "key":"111-A11-000111",
                  "taxYear":2016,
                  "realTimeStatus":"Available",
                  "payments":[
                     {
                        "date":"2015-11-26",
                        "amountYearToDate":1000,
                        "taxAmountYearToDate":200,
                        "nationalInsuranceAmountYearToDate":120,
                        "amount":1000,
                        "taxAmount":200,
                        "nationalInsuranceAmount":120,
                        "payFrequency":"FortNightly"
                     }
                  ],
                  "endOfTaxYearUpdates":[
                     {
                        "date":"2017-03-26",
                        "adjustments":[
                           {
                              "type":"NationalInsuranceAdjustment",
                              "amount":50.00
                           }
                        ]
                     }
                  ]
               }
            ],
            "taxDistrictNumber":"000",
            "payeNumber":"P00",
            "sequenceNumber":1
         }
      ]
   },
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


