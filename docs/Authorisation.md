# Authorisation

Authorisation works both via Role-Based Access Control (RBAC) and Owner-Based Access Control.

### Role-Based (Global) Access Control

A global `Superuser` role is available to enable system-wide authorisation for creating and accessing entities.

A user can have at most one global role.

### Ownership-Based Access Control

Access to entities is granted based on ownership of the entity, making it contextual.
A user can be a contributor to one document, the owner of another, and unable to access others.

### Access

`Admin`s can fetch all entities, regardless of ownership.

`Superuser`s can fetch all entities except UserEntity, for which they can only fetch if they are the owner.

`Owner`s can fetch only the entities they own.

## Privilegies

There's currently no distinction on what a user can once it obtain access to an entity,
but only which users can access certain entities.

In other words, once a user has access to an entity, she is authorised to read, write and delete it.
There is currently no restrictions on creation aside from the requiring an authenticated user.

The former should change when introducing contributors, which should be only allowed to edit and possibly
create entities. The latter could change with, for instance, limiting the number of entities of a certain
kind a user is authorised to create.

## Future Considerations

 * More fine-grained control to access resources, for instance 'can delete', can create', 'can view'.
 Currently there is a single 'can access' control, which either givel full or no access to entities.

 * Attribute-based Access Control: authorisation can be denied based on resources, location, date and time. It will be necessary to limit the number of entities a user can create,
 e.g number of projects, documents per projects, etc.
