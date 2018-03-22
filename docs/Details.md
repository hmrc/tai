Details
----
  Returns the contents of the `MANIFEST.MF` file in the main jar of the microservice as a JSON object.
  See [sbt-git-stamp](https://github.com/hmrc/sbt-git-stamp) for more specific details about the contents of the manifest.

* **URL**

  `/admin/details`
  
  or
  
  `/admin/detail/:key`

* **Method:**
  
  `GET` 
  
*  **URL Params**

   **Required:**
 
   `key=[string]`

* **Success Response:**
  
  * **Code:** 200 <br />
    **Content:** `{  }`
