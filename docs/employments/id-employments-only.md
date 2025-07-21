Get Employment details by employment id and tax year
----------------
  Retrieves employments by provided id and tax year.

* **URL**

  `/tai/:nino/employment-only/:id/years/:year `

* **Method:**

  `GET`

*  **URL Params**

   **Required:**

   `nino=[Nino]`

   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

   `id=Int`

   The tax year as an integer, e.g. 2014.

   `year=Int`

* **Success Response:**

  * **Code:** 200 <br />
    **Content:**

All fields are required except payrollNumber, endDate and cessationPay.

TaxCodeIncomeStatus is a value from Live, NotLive, PotentiallyCeased and Ceased

TaxCodeIncomeComponentType is a value from: EmploymentIncome, PensionIncome, JobSeekerAllowanceIncome, OtherIncome

Date fields are in ISO-8601 format (YYYY-MM-DD)
* **Content:**

```scala

```json
{
  "data":{
      "name": "Employer name",
      "employmentStatus": "TaxCodeIncomeStatus",
      "payrollNumber": "abcd1234",
      "startDate": "2000-01-01",
      "endDate": "2000-12-31",
      "taxDistrictNumber": "000",
      "payeNumber": "p000",
      "sequenceNumber": 1,
      "cessationPay": 1.0,
      "hasPayrolledBenefit": true,
      "employmentType": "TaxCodeIncomeComponentType"
  }
}
```

* **Error Response:**

    * **Code:** 401 UNAUTHORIZED <br />
      Unauthorized access, the user is not authenticated or does not have permission to access this resource. <br />

    * **Code:** 404 NOT_FOUND <br />
      No employment found for the given year or NINO or employment id.<br />

    * **Code:** 500 INTERNAL_SERVER_ERROR <br />
      Internal server error, something went wrong on the server side. <br />

    * **Code:** 502 BAD_GATEWAY <br />
      A dependency returned an error <br />
