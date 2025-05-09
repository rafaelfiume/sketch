# Error Codes

| Error Code   | Description                           | Status | Possible Causes                                    | Resolution Steps                        |
|--------------|---------------------------------------|------- |----------------------------------------------------|-----------------------------------------|
| 1000         | Semantically invalid credentials      | 422    | Username or password doesn't comply with rules     | Check username and password rules       |
| 1001 to 1003 | Attempt to login failed               | 401    | Wrong credentials or inactive account              | Check credentials and account           |
| 1011         | Attempt to verify jwt failed          | 401    | Invalid jwt or wrong signature                     | Check Jwt is valid                      |
| 1100         | Script to setup account failed        | N/A    | Invalid script parameter(s)                        | Check parameters                        |
| 1200         | No further act. req. to soft del acc. | 409    | Account already pending permanent deletion         | Client should check requests            |
| 1201         | No account to mark for deletion       | 404    | Account not found                                  | Client should check requests            |
| 3000         | Lack of permission to perform action  | 403    | Attempt to access secured resource                 | Reacess Api rate limit                  |
| 9000         | Unprocessable entity                  | 422    | Structurally unexpected json                       | Check the Api contract                  |
| 9011         | Semantically invalid document         | 422    | Document doesn't comply with rules                 | Check documents rules                   |

## Tips

 * Whenever rules need to be consulted, look for `#validated` functions and their associated specs.
