Annualise Income
----
  Annualises Year to Date Income for the current financial year

* **URL**

  `/tai/calculator/annualise-income`

* **Method:**

  `POST`

*  **URL Params**

   None
   
   **Required:**

   None

* **Payload**

```json
{
   "amountYearToDate":1000.00,
   "employmentStartDate" : "2015-10-06",
   "paymentDate" : "2015-10-07"
}
```

* **Success Response:**

  * **Code:** 200 <br />
    **Content:**

```json
{
   "annualisedAmount": 500.00
}
```

* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />



