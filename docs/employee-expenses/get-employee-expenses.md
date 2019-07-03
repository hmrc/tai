Employee Expenses
--------------------------
  Retrieves IABD data based on IABD type for a tax year
  
* **URL**

  `/tai/:nino/tax-account/:year/expenses/employee-expenses/:iabd`

* **Method:**
  
  `GET`
  
*  **URL Params**

   **Required:**
 
   `nino=[Nino]` 
   
   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

   `year=Int`
   
   `iabd=Int`
   
* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 
   
```json
       {
            "nino": "AB123456A",
            "sequenceNumber": 201600003,
            "taxYear": 2018,
            "type": 56,
            "source": 26,
            "grossAmount": 120,
            "receiptDate": null,
            "captureDate": null,
            "typeDescription": "Flat Rate Job Expenses",
            "netAmount": null
       }
```

* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/:nino/tax-account/:year/expenses/employee-expenses/:iabd"}`

  OR when a user does not exist

  * **Code:** 404 NOT_FOUND <br />

  OR anything else

  * **Code:** 500 INTERNAL_SERVER_ERROR <br />
 