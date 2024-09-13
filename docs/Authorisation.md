# Authorisation

Authorisation works both via Role-Based Access Control (RBAC) and Owner-Based Access Control.

### Role-Based (Global) Access Control

Global roles are also available to enable system-wide access that can create and access entities anywhere. 

A `Superuser` can access all entities (documents for now) created by 'regular' users.

### Ownership-Based Access Control

Entities access are granted based on ownership of the entity.


## Entities

Owner and Superuser:
 * Has full control over entity (view, edit, delete, assign or revoke permissions to other users).
 
Contributor:
 * Can create or access an entity, but cannot delete it. (?)


## Future Considerations

 * More fine-grained control to access resources, for instance 'can delete', can create', 'can view'.
 Currently there is a single 'can access' control, which either givel full or no access to entities.

 * Contextual Access Constrol: authorisation can be denied based on resources, location, date and time. "Access decisions are made based on user attributes, entity attributes, and environment conditions (e.g., time of access, location)."
