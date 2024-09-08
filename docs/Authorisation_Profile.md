# Authorisation & Profile

Role-Based Access Control (RBAC): Users are assigned roles (e.g., admin, editor, viewer), and access to resources is granted based on these roles.
Attribute-Based Access Control (ABAC): Access decisions are made based on user attributes, entity attributes, and environment conditions (e.g., time of access, location).
Owner-Based Access Control: Specifically designed for situations where access is granted based on ownership of the entity.

## Entities

Owner:
 * Has full control over entity (view, edit, delete, assign or revoke permissions to other users).
 
Editor:
 * Can create or contribute to the entity, including deleting it.
 * It cannot alter or manage permissions to other users.

Note that `owner` is a superset of `editor`.
Note that support for `editor` is coming soon.

## Relationship Between Users and Roles

* A user can have more than one role

## Future Considerations

* Hierarchical Roles: a role can be composed by others and inherit their permissions
* Contextual Access Constrol: authorisation can be denied based on resources, location, date and time.
