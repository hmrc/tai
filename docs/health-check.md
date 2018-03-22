Health Check
----
  Pings all dependent microservices and returns a response indicating their availability.

* **URL**

  `admin/healthcheck`

* **Method:**
  
  `GET` 
  
* **Success Response:**
  
  * **Code:** 200 <br />
    **Content:** `"Healthy"`
 
* **Error Response:**

  * **Code:** 500 INTERNAL_SERVER_ERROR <br />
    **Content:** 

`nps:ConnectException:Connection refused: localhost/127.0.0.1:8123 to http://localhost:8123/ping/ping`
`datastream:ConnectException:Connection refused: localhost/127.0.0.1:8100 to http://localhost:8100/ping/ping`

* **Notes:**

   If there are any failures, they will be as in the format above, with the service name first, then a colon, then the message of the error that occurred while trying to ping that service.
