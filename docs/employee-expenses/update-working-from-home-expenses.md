Working From Home Employee Expenses
--------------------------
  Updates working from home employee expenses data based on IABD type for a tax year

* **URL**

  `/tai/:nino/tax-account/:year/expenses/working-from-home-employee-expenses/:iabd`

* **Method**

  `POST`

*  **URL Params**

   **Required:**
 
   `nino=[Nino]` 
   
   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

   `year=Int`
   
   `iabd=Int`

* **Payload**

```json
{
  "version": 1,
  "grossAmount": 200
}
```

* **Success Response:**

  * **Code:** 200 <br />
  
* **Error Response:**
  
    * **Code:** 401 UNAUTHORIZED <br />
      **Content:** `{"statusCode":401,"message":"Authorisation refused for access to POST /tai/:nino/tax-account/:year/expenses/working-from-home-employee-expenses/:iabd"}`
  
    OR when a user does not exist
  
    * **Code:** 404 NOT_FOUND <br />
  
    OR anything else
  
    * **Code:** 500 INTERNAL_SERVER_ERROR <br />
   