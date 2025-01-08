Tax Account for Individuals(TAI)
================================================

Allows users to view and edit their paye tax information

Requirements
------------

This service is written in [Scala 2.13](http://www.scala-lang.org/) and [Play 3.0](http://playframework.com/), so needs at least a [JRE 21](http://www.oracle.com/technetwork/java/javase/downloads/index.html) to run.

API
---

| *Task*                                                                                                   | *Supported Methods* | *Description*                                                                                                                                                                              |
|----------------------------------------------------------------------------------------------------------|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ```/tai/:nino/person ```                                                                                 | GET                 | The end point retrieves designatory details for the given nino.                                                                                                                            |
| ```/tai/:nino/employments/years/:year ```                                                                | GET                 | The end point retrieves all employments for a given year with Annual Account information [More...](docs/employments/annual-account-employments.md)                                         |
| ```/tai/:nino/employments/years/:year/update ```                                                         | POST                | The end point updates the incorrect employment details [More...](docs/employments/update-employments.md)                                                                                   |
| ```/tai/:nino/employments/:id ```                                                                        | GET                 | The end point retrieves employments by provided id [More...](docs/employments/id-employments.md)                                                                                           |
| ```/tai/:nino/employments ```                                                                            | POST                | The end point adds a new employment [More...](docs/employments/employments.md)                                                                                                             |
| ```/tai/:nino/employments/:id/end-date ```                                                               | PUT                 | The end point allows the consumer to update the end date for the employment [More...](docs/employments/update-enddate-employment.md)                                                       |
| ```/tai/:nino/employments/:id/reason ```                                                                 | POST                | The end point updates the incorrect employment details for current year [More...](docs/employments/reason-employment.md)                                                                   |
| ```/tai/:nino/pensionProvider ```                                                                        | POST                | The end point adds a new pension provider for the user [More...](docs/pensions/pension-provider.md)                                                                                        |
| ```/tai/:nino/pensionProvider/:id/reason ```                                                             | POST                | The end point updates the incorrect pension details for the current year [More...](docs/pensions/update-pension-provider.md)                                                               |
| ```/tai/:nino/tax-account/:year/benefits ```                                                             | GET                 | The end point provides fetches the benefits for the provided nino and tax year [More...](docs/benefits/benefits.md)                                                                        |
| ```/tai/:nino/tax-account/tax-components/benefits/company-cars ```                                       | GET                 | The end point fetches all the company car benefits for nino [More...](docs/benefits/company-cars.md)                                                                                       |
| ```/tai/:nino/tax-account/tax-component/employments/:employmentId/benefits/ended-benefit ```             | POST                | The end point would submit a request to remove a company benefit [More...](docs/benefits/remove-company-benefit.md)                                                                        |
| ```/tai/:nino/tax-account/:year/income/tax-code-incomes ```                                              | GET                 | The end point fetches tax code incomes for a given nino and given year [More...](docs/incomes/taxcode-incomes.md)                                                                          |
| ```/tai/:nino/tax-account/:year/income ```                                                               | GET                 | The end point fetches incomes for a given nino and a given year [More...](docs/incomes/incomes.md)                                                                                         |
| ```/tai/:nino/tax-account/snapshots/:snapshotId/incomes/tax-code-incomes/:employmentId/estimated-pay ``` | PUT                 | The end point updates the estimated pay [More...](docs/incomes/update-pay.md)                                                                                                              |
| ```/tai/:nino/tax-account/year/:year/income/:incomeType/status/:status```                                | GET                 | The end point matches tax code incomes to employments [More...](docs/incomes/matched-incomes.md)                                                                                           |
| ```/tai/:nino/employments/year/:year/status/ceased```                                                    | GET                 | The end point returns ceased non-matching employments [More...](docs/incomes/nonmatching-incomes.md)                                                                                       |
| ```/tai/:nino/tax-account/:year/expenses/employee-expenses/:iabd```                                      | GET                 | The end point returns IABD data based on IABD type for a tax year [More...](docs/employee-expenses/get-employee-expenses.md)                                                               |
| ```/tai/:nino/tax-account/:year/expenses/employee-expenses/:iabd```                                      | POST                | The end point updates IABD data based on IABD type for a tax year [More...](docs/employee-expenses/update-employee-expenses.md)                                                            |
| ```/tai/:nino/tax-account/:year/expenses/working-from-home-employee-expenses/:iabd```                    | POST                | The end point updates working from home employee expenses data based on IABD type for a tax year [More...](docs/employee-expenses/update-working-from-home-expenses.md)                    |
| ```/tai/:nino/tax-account/:year/tax-components ```                                                       | GET                 | The end point provides a list of coding components [More...](docs/taxcomponents/tax-components.md)                                                                                         |
| ```/tai/:nino/tax-account/:year/summary ```                                                              | GET                 | The end point fetches annual tax account summary [More...](docs/taxaccount/summary.md)                                                                                                     |
| ```/tai/:nino/tax-account/:year/total-tax ```                                                            | GET                 | The end point fetches the total tax values for annual tax account [More...](docs/taxaccount/total-tax.md)                                                                                  |
| ```/tai/:nino/tax-account/tax-code-change/exists ```                                                     | GET                 | The end point returns a boolean which indicates if there has been a tax code change in the tax year [More...](docs/taxaccount/tax-code-change-exists.md)                                   |
| ```/tai/:nino/tax-account/tax-code-change ```                                                            | GET                 | The end point returns details of the current and previous operated tax codes for a given nino [More...](docs/taxaccount/tax-code-change.md)                                                |
| ```/tai/:nino/tax-account/tax-free-amount-comparison ```                                                 | GET                 | The end point returns the current and previous IABD information relating to Income Sources and Total Liabilities for a given nino [More...](docs/taxaccount/tax-free-amount-comparison.md) |
| ```/tai/:nino/tax-account/tax-code-mismatch ```                                                          | GET                 | The end point returns mismatch flag and list of confirmed and unconfirmed tax codes                                                                                                        |
| ```/tai/:nino/tax-account/:year/tax-code/latest ```                                                      | GET                 | The end point returns most recent tax codes for the given nino and tax year                                                                                                                |
| ```/tai/session-cache ```                                                                                | DELETE              | The end point will invalidate the cache.                                                                                                                                                   |
                                                                                        

Configuration
-------------

All the microservices used by TAI require host and port settings, for example:

| *Key*                                | *Description*               |
|--------------------------------------|-----------------------------|
| `microservice.services.nps-hod.host` | The host of the NPS service |
| `microservice.services.nps-hod.port` | The port of the NPS service |
| `microservice.services.nps-hod.path` | The path of the NPS service |

Only the nps and hip microservices require a path.


How to test the project
===================

Unit Tests
----------
- **Unit test the entire test suite:**  `sbt test`

- **Unit test a single spec file:**  sbt "test:testOnly *fileName"   (for e.g : `sbt "test:testOnly *IncomeControllerSpec"`)


Integration tests
----------------
- **`sbt it/test`**


Acceptance tests
----------------
To verify the acceptance tests locally, follow the steps:
- start the sm2 container for TAI profile: `sm2 --start TAI_ALL`
- stop `TAI` process running in sm2: `sm2 --stop TAI`
- launch tai in terminal and execute the following command in the TAI project directory: <br> `sbt "run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes"`
- open [tai-acceptance-test-suite](https://github.com/hmrc/tai-acceptance-test-suite) repository in the terminal and execute the script: `./run_tests_local.sh`
