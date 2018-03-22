The TAI root object
----
  Fetches the PAYE root object.
  
  This contains some information about the PAYE user, as well as links for further interactions with their data.

* **URL**

  `tai/:nino`

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
   "nino":"$NINO",
   "version":17,
   "title":"Mr",
   "firstName":"name1",
   "secondName":"name1",
   "surname":"name1",
   "name":"name1",
   "dateOfBirth":"NpsDate(1969-03-05)"
}
```
 
* **Error Response:**

  * **Code:** 400 BAD_REQUEST <br />
    **Content:** `{"statusCode":400,"message":"Cannot parse parameter 'nino' with value '$NINO' as 'Nino'"}`

 OR

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"statusCode":401,"message":"Authorisation refused for access to GET /tai/$NINO"}`

  OR when a user does not exist

  * **Code:** 404 NOT_FOUND <br />


