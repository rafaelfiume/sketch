# Error Handling (Draft)

'Error' means any kind of error, being its source customers (e.g. input validation) or system errors.

`ErrorInfo` should:
 - Be the response payload provided by endpoints in case of errors
 - Be tracked - see 'Tracking Errors' session below
 - Have a severity assigned to it.

 Severety levels are:
 - Info:
 - Warning:
 - Critical: Prevents the system for functioning and are not recoverable (e.g system or resource is down).

`ErrorCode`:
- Should be used to facilitate troubleshooting
- Should not be exposed to end customers
- Should conform to simple and clear semantic rules
- Must not leak details that could compromise system integrity

### We have a message for you

Allow customers to interact with support through the application when severity is equal or above `Warning`.

"
Ops, something hasn't happened quite as expected.
Thank you for your patience and for helping us to improve our services. 😇
"

Button 1: "Ok, it happens"
Button 2: "Inform the support team"

Allow customers to express him/herself by adding optional details when reporting an issue.

"
Would you like to provide more details on 'How this issue impacts you or your business'?
"

## Tracking Errors

### Retention Rules

All errors will be tracked, but their retention will vary according to their severity.

## Error code semantic rules

[MODULE (3 chars)][SEVERITY (A to C)][0-100]


Feito com ❤️ por Artigiani.
