# Authorisation & Profile

Authorisation works both via Role-Based Access Control (RBAC) and Owner-Based Access Control.

Entities access are granted based on ownership of the entity.
Global roles are also available (e.g. 'superuser') to enable system-wide access that can create and access entities anywhere.

Entities created by anonoymous users (guests) must be assigned to superusers.

The `access_control` table is "the central source for both global admin accessand contextual ownership".

## Entities

Owner:
 * Has full control over entity (view, edit, delete, assign or revoke permissions to other users).
 
Contributor:
 * Can create or access an entity, but cannot delete it. (?)

## Future Considerations

 * More fine-grained control to access resources, for instance 'can delete', can create', 'can view'.
 Currently there is a single 'can access' control, which either givel full or no access to entities.

 * Contextual Access Constrol: authorisation can be denied based on resources, location, date and time. "Access decisions are made based on user attributes, entity attributes, and environment conditions (e.g., time of access, location)."

 * Create an 'admin' role. Note that a role-based (global) 'admin' user
 would stil have entity-based (contextual) access to an entity when she creates one.
 In practice, that means an entry in the `access_control` table. (Is this note relevant for the docs?)



The plan:

Enable 'superuser' role-based entities access (system-wide access to all entities)
1) Associate a user with 'superuser' role (`role_based_access_control` or `global_access_control`)
1) Change user-registre script to allow defining a superuser
1) `fetchAllAuthorisedEntityIds` should return all entities in case 'superuser' ?

By the end of this stream, I should be able to registre a superuser, login in the ui and 
enable superuser to access all entities (documents for now) created by 'regular' users.
