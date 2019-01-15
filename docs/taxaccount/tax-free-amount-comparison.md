Tax Free Amount Comparision
----------------------------
  The end point returns the current and previous IABD information relating to Income Sources and Total Liabilities for a given nino

* **URL**

  `/tai/:nino/tax-account/tax-free-amount-comparison`

* **Method:**

  `GET`

* **Success Response:**

  * **Code:** 200 <br />
    **Content:**

```json
{
    "data": {
        "previous": [
            {
                "componentType": "PersonalAllowancePA",
                "amount": 11850,
                "description": "Personal Allowance",
                "iabdCategory": "Allowance",
                "inputAmount": 11850
            },
            {
                "componentType": "CarBenefit",
                "employmentId": 1,
                "amount": 2000,
                "description": "Car Benefit",
                "iabdCategory": "Benefit"
            }
        ],
        "current": [
            {
                "componentType": "PersonalAllowancePA",
                "amount": 10000,
                "description": "personal allowance",
                "iabdCategory": "Allowance"
            },
            {
                "componentType": "StatePension",
                "amount": 25557,
                "description": "state pension / state benefits",
                "iabdCategory": "NonTaxCodeIncome"
            },
            {
                "componentType": "BankOrBuildingSocietyInterest",
                "amount": 7500,
                "description": "savings income taxable at higher rate",
                "iabdCategory": "NonTaxCodeIncome"
            },
            {
                "componentType": "CarBenefit",
                "employmentId": 1,
                "amount": 2222,
                "description": "Car Benefit",
                "iabdCategory": "Benefit"
            },
            {
                "componentType": "CarBenefit",
                "employmentId": 1,
                "amount": 2223,
                "description": "Car Benefit",
                "iabdCategory": "Benefit"
            },
            {
                "componentType": "CarBenefit",
                "employmentId": 2,
                "amount": 4444,
                "description": "Fuel Benefit",
                "iabdCategory": "Benefit"
            }
        ]
    },
    "links": []
}
```
* **Error Response:**

 * **Code:** 400 <br />
    **Content:**

```json
{
    "reason": "Could not generate TaxFreeAmountComparison - GET of 'http://<server>:9332/nps-hod-service/services/nps/person/$nino/tax-account/2018/calculation' failed. Caused by: "
}
```