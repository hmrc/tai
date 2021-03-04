Tax Account for Individuals(TAI)
===============================================

Allows users to view and edit their paye tax information

Requirements
------------

This service is written in [Scala](http://www.scala-lang.org/) and [Play 2.5](http://playframework.com/), so needs at least a [JRE 1.8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) to run.

API
---

| *Task* | *Supported Methods* | *Description* |
|--------|----|----|
| ```/tai/:nino/employments/years/:year ```  | GET | Retrieves all employments for a given year with Annual Account information [More...](docs/employments/annual-account-employments.md) |
| ```/tai/:nino/employments/years/:year/update ```  | POST | The end point updates the incorrect employment details [More...](docs/employments/update-employments.md)|
| ```/tai/:nino/employments/:id ```  | GET | Retrieves employments by provided id [More...](docs/employments/id-employments.md)|
| ```/tai/:nino/employments ```  | POST | The end point adds a new employment [More...](docs/employments/employments.md)|
| ```/tai/:nino/employments/:id/end-date ```  | PUT | The end point allows the consumer to update the end date for the employment [More...](docs/employments/update-enddate-employment.md)|
| ```/tai/:nino/employments/:id/reason ```  | POST | The end point updates the incorrect employment details for current year [More...](docs/employments/reason-employment.md)|
| ```/tai/:nino/pensionProvider ```  | POST | The end point adds a new pension provider for the user [More...](docs/pensions/pension-provider.md) |
| ```/tai/:nino/pensionProvider/:id/reason ```  | POST | The end point updates the incorrect pension details for the current year [More...](docs/pensions/update-pension-provider.md) |
| ```/tai/:nino/tax-account/:year/benefits ```  | GET | The end point provides fetches the benefits for the provided nino and tax year [More...](docs/benefits/benefits.md) |
| ```/tai/:nino/tax-account/tax-components/employments/:id/benefits/company-car ```  | GET | The end point fetches the benefits for employment [More...](docs/benefits/employment-company-car.md) |
| ```/tai/:nino/tax-account/tax-components/employments/:empId/benefits/company-car/:carId/withdrawn ```  | PUT | The end point would submit a request to withdraw a company car [More...](docs/benefits/withdraw-company-car.md) |
| ```/tai/:nino/tax-account/tax-components/benefits/company-cars ```  | GET | The end point fetches all the company car benefits for nino [More...](docs/benefits/company-cars.md) |
| ```/tai/:nino/tax-account/income/savings-investments/untaxed-interest ```  | GET | The end point fetches non taxed interest for a given nino [More...](docs/incomes/untaxed-interest.md) |
| ```/tai/:nino/tax-account/income/savings-investments/untaxed-interest/bank-accounts ```  | GET | The end point fetches bank details for nino [More...](docs/incomes/bbsi-details.md) |
| ```/tai/:nino/tax-account/income/savings-investments/untaxed-interest/bank-accounts/:id ```  | GET | The end point fetches bank account details a given nino and id [More...](docs/incomes/bbsi-account.md) |
| ```/tai/:nino/tax-account/income/savings-investments/untaxed-interest/bank-accounts/:id ```  | DELETE | The end point removes bank account for a given nino and id [More...](docs/incomes/remove-account.md) |
| ```/tai/:nino/tax-account/income/savings-investments/untaxed-interest/bank-accounts/:id/closedAccount ```  | PUT | The end point submits a request to close the bank account [More...](docs/incomes/close-bank-account.md) |
| ```/tai/:nino/tax-account/income/savings-investments/untaxed-interest/bank-accounts/:id/interest-amount ```  | PUT | The end point submits a request to update bank account interest [More...](docs/incomes/update-bank-account.md) |
| ```/tai/:nino/tax-account/:year/income/tax-code-incomes ```  | GET | The end point fetches tax code incomes for a given nino and given year [More...](docs/incomes/taxcode-incomes.md) |
| ```/tai/:nino/tax-account/:year/income ```  | GET | The end point fetches incomes for a given nino and a given year [More...](docs/incomes/incomes.md) |
| ```/tai/:nino/tax-account/snapshots/:snapshotId/incomes/tax-code-incomes/:employmentId/estimated-pay ```  | PUT | The end point updates the estimated pay [More...](docs/incomes/update-pay.md) |
| ```/tai/:nino/tax-account/:year/tax-components ```  | GET | The end point provides a list of coding components [More...](docs/taxcomponents/tax-components.md) |
| ```/tai/:nino/tax-account/:year/summary ```  | GET | The end point fetches annual tax account summary [More...](docs/taxaccount/summary.md) |
| ```/tai/:nino/tax-account/:year/total-tax ```  | GET | The end point fetches the total tax values for annual tax account [More...](docs/taxaccount/total-tax.md) |
| ```/tai/:nino/tax-account/tax-code-change/exists ```  | GET | The end point returns a boolean which indicates if there has been a tax code change in the tax year [More...](docs/taxaccount/tax-code-change-exists.md) |
| ```/tai/:nino/tax-account/tax-code-change ```  | GET | The end point returns details of the current and previous operated tax codes for a given nino [More...](docs/taxaccount/tax-code-change.md) |
| ```/tai/:nino/tax-account/tax-free-amount-comparison ```  | GET | The end point returns the current and previous IABD information relating to Income Sources and Total Liabilities for a given nino [More...](docs/taxaccount/tax-free-amount-comparison.md) |
|```/tai/:nino/tax-account/year/:year/income/:incomeType/status/:status``` | GET | The end point matches tax code incomes to employments [More...](docs/incomes/matched-incomes.md) |
|```/tai/:nino/employments/year/:year/status/ceased``` | GET | The end point returns ceased non-matching employments [More...](docs/incomes/nonmatching-incomes.md) |
|```/tai/:nino/tax-account/:year/expenses/employee-expenses/:iabd``` | GET | The end point returns IABD data based on IABD type for a tax year [More...](docs/employee-expenses/get-employee-expenses.md) |
|```/tai/:nino/tax-account/:year/expenses/employee-expenses/:iabd``` | POST | The end point updates IABD data based on IABD type for a tax year [More...](docs/employee-expenses/update-employee-expenses.md) |

Deprecated API Endpoints (Not advised to use)
---

| *Task* | *Supported Methods* | *Description* |
|--------|----|----|
| ```/tai/:nino``` | GET | Returns the ```TaiRoot``` for the given nino. [More...](docs/tai-root.md)  |
| ```/tai/:nino/incomes/:taxYear/update``` | POST | Update ```Income``` IABDTypes  for the given list of incomes. [More...](docs/update-incomes-iabd.md)  |
| ```/tai/:nino/incomes/:taxYear/update-without-saving ``` | POST | Update the ```Income``` IABDTypes  for the given list of incomes. [More...](docs/update-incomes-iabd.md)  |
| ```/tai/calculator/annualise-income ``` | POST | Annualises year to date incomes for a given amount, a start date and an end date. [More...](docs/annualise-income.md)  |

Configuration
-------------

All configuration is namespaced by the `run.mode` key, which can be set to `Dev` or `Prod` - note this is independent of Play's concept of mode.

All the other microservices used by TAI require host and port settings, for example:

| *Key*                    | *Description* |
| ------------------------ | ----------- |
| `microservice.services.nps-hod.host` | The host of the NPS service |
| `microservice.services.nps-hod.port` | The port of the NPS service |
| `microservice.services.nps-hod.path` | The path of the NPS service |

Only nps microservice requires a path.

