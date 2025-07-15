Annual Account Employments
----
Retrieves annual accounts for all employments for a given tax year

* **URL**

  `/tai/:nino/rti-payments/years/:year `

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

All fields are required except duplicate, payments and endOfTaxYearUpdates.

RealTimeStatus is a value from Available, TemporarilyUnavailable and Unavailable

PaymentFrequency is a value from FortNightly, FourWeekly, Monthly, Quarterly, BiAnnually, Annually, OneOff and Irregular

AdjustmentType is a value from: NationalInsuranceAdjustment, TaxAdjustment and IncomeAdjustment

Date fields are in ISO-8601 format (YYYY-MM-DD)
* **Content:**

```scala

```json
{
   "data":{
     "sequenceNumber": 1,
     "taxYear": 2025,
     "realTimeStatus": "RealTimeStatus",
     "payments": [
       {
         "date": "LocalDate",
         "amountYearToDate": 1.0,
         "taxAmountYearToDate": 1.0,
         "nationalInsuranceAmountYearToDate": 1.0,
         "amount": 1.0,
         "taxAmount": 1.0,
         "nationalInsuranceAmount": 1.0,
         "payFrequency": "PaymentFrequency",
         "duplicate": true
       }
     ],
     "endOfTaxYearUpdates": [
       {
         "date": "LocalDate", 
         "adjustments": [ 
           {
     `       "type": "AdjustmentType", 
             "amount": 1.0
           }
         ]
       }
     ]
   }
}
```

* **Error Response:**

    * **Code:** 401 UNAUTHORIZED <br />
      Unauthorized access, the user is not authenticated or does not have permission to access this resource. <br />

    * **Code:** 404 NOT_FOUND <br />
      No payments found for the given year or NINO.<br />

    * **Code:** 500 INTERNAL_SERVER_ERROR <br />
      Internal server error, something went wrong on the server side. <br />

    * **Code:** 502 BAD_GATEWAY <br />
      A dependency returned an error <br />
