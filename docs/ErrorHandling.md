# Error Handling (Draft)

'Error' means any kind of error, being its source customers (e.g. input validation) or system errors.

`ErrorInfo` should:
 - Be the response payload provided by endpoints in case of errors
 - Be tracked - see 'Tracking Errors' session below

## Tracking Errors

### Retention Rules

All errors will be tracked, but their retention will vary according to their severity.

Feito com ❤️ por Artigiani.
