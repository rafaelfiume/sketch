
### For the next Clean-up PR

  * Favour derivedDecoders and derivedEncoders
  * Write contract tests to conver all request and response payloads
  * Move decoders and encoders to a json.Codecs module
  * TODO Merge the following 3 tests?
  * TODO Make it return Either[MarkForDeletionError, ScheduledAccountDeletion]
  * TODO Explore auto-derivation?

### Missing steps:

1)  Define process to enable Admin to reactivate an account
1) clean up all entities upon permanent deletion

Use callback to delete all entities of user with deleted account
    (search for '1. REST-based Event Notifications (Webhook-Style)')
    (secure call to callback, it needs to be authenticated; make it atomic; retries; log and track (metrics) failed attempts)

... Rest-based Event Notification (Webhook Style)...
... use a temporary shared secret solution to authenticate the auth part of the service when invoking
the `/purge-user-entities` endpoint.