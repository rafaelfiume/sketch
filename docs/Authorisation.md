# Authorisation

Authorisation works both via Role-Based Access Control (RBAC) and Owner-Based Access Control.

### Role-Based (Global) Access Control

A global `Superuser` role is available to enable system-wide authorisation for creating and accessing entities. 

### Ownership-Based Access Control

Access to entities is granted based on ownership of the entity, making it contextual.
A user can be a contributor to one document, the owner of another, and unable to access others.


## Future Considerations

 * More fine-grained control to access resources, for instance 'can delete', can create', 'can view'.
 Currently there is a single 'can access' control, which either givel full or no access to entities.

 * Contextual Access Constrol: authorisation can be denied based on resources, location, date and time. "Access decisions are made based on user attributes, entity attributes, and environment conditions (e.g., time of access, location)."
