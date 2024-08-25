# Authorisation & Profile

Role-Based Access Control (RBAC): Users are assigned roles (e.g., admin, editor, viewer), and access to resources is granted based on these roles.
Attribute-Based Access Control (ABAC): Access decisions are made based on user attributes, resource attributes, and environment conditions (e.g., time of access, location).
Owner-Based Access Control: Specifically designed for situations where access is granted based on ownership of the resource.

## Resouces Ownership

 * Every `resource` (e.g document, product item) must be associated with an `owner`.
 * The `owner` is not necessarily the `user` that `creates` the `resource`, e.g. uploading a document.
 In that case, the `user` is the `editor` and the business reprentative is the `owner`.
 * Both `owner` and `editor` must be `user`s registered in the system.
 * Both `owner` and `editor` must be `authenticated` by the system in order to create a `resource`.
 * In case of anonymous creation of resource, the `owner` is the business representative.
 * A `user` should have `access` to all `owned` resources.
 * A `user` should have `access` to all resources in which she has been grante `editor`.

Owner:
 * Has full control over resource (view, edit, delete, assign or revoke permissions to other users).
 
Editor:
 * Can create or contribute to the resource, including deleting it.
 * It cannot alter or manage permissions to other users.

Note that `owner` is a superset of `editor`.
Note that support for `editor` is coming soon.

## Relationship Between Users and Roles

* A user can have more than one role

## Future Considerations

* Hierarchical Roles: a role can be composed by others and inherit their permissions
* Contextual Access Constrol: authorisation can be denied based on resources, location, date and time.


Consider creating a resouce_access table:

```

CREATE TABLE users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE resources (
    resource_id SERIAL PRIMARY KEY,
    owner_id INT REFERENCES users(user_id),
    title VARCHAR(255),
    content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

#CREATE TABLE document_access
CREATE TABLE resource_access (
    # how to make it reference to a generic id (e.g. documents, workflow, etc)?
    resource_id INT REFERENCES resources(resource_id),
    user_id INT REFERENCES users(user_id),
    #access_level VARCHAR(50),  -- e.g., 'view', 'edit'
    role VARCHAR(50) CHECK (role IN ('Editor', 'Viewer')),
    PRIMARY KEY (resource_id, user_id)
);
```