# Access Control Module

Defines **how system grants or denies access to entities**, combining Role-Based Access Control (RBAC) and Ownership-Based Access Control.

| Access Control  | Permission Level         |
|-----------------|--------------------------|
| Role-based      | Global permission        |
| Ownership-based | Entity-level             |

> ⚠️ **Work in Progress**: This system is in early-stage development.
> See [Section 3: Future Directions](#3-future-directions) for planned changes.

---

**Table of Contents**

1. [Goals](#1-goals)
2. [Current Rules](#2-current-rules)
    - [2.1 Global Permissions (Role-Based Access Control)](#21-global-permissions-role-based-access-control)
    - [2.2 Entity-Level Permissions (Ownership-Based Access Control)](#22-entity-level-permissions-ownership-based-access-control)
    - [2.3 Permission Rules](#23-permission-rules)
    - [2.4 Privileges & Current Limitations](#24-privileges--current-limitations)
3. [Future Directions](#3-future-directions)
4. [Implementation Reference](#4-implementation-reference)
5. [References](#5-references)


## 1. Goals

The authorisation system must:
  * Control access to entities based on **user roles** and **ownership**
  * Keep **rules simple and predictable** as the system evolves
  * **Allow extensions** to fine-grained permissions and more evolved policies in the future.


## 2. Current Rules

These are the heart of the authorisation system.

### 2.1 Global Permissions (Role-Based Access Control)

* Roles define global permissions that are **applied across all entities**
* A user can have **at most one global role**.

| Global Role        | Description |
|--------------------|-------------|
| **Admin**          | System-wide access to all entities |
| **Superuser**      | System-wide access to all entities, except for user/account management |
| **No Global Role** | Can only access their own resources |

### 2.2 Entity-Level Permissions (Ownership-Based Access Control)

Ownership-Based control is a simplified form of [Relationship-Based Access Control (ReBAC)](https://en.wikipedia.org/wiki/Relationship-based_access_control), and a project-specific pattern.

* A user that creates an entity is its **owner**
* An entity has **exactly one owner**
* Ownership grants **full permissions**: read, edit, delete.


See the test `"grants a user ownership, and thus access, to an entity"` in [PostgresAccessControlSpec](../storage/src/it/scala/org/fiume/sketch/storage/authorisation/postgres/PostgresAccessControlSpec.scala) for the definitive implementation. For integration examples, see [UsersManagerSpec](../auth/src/test/scala/org/fiume/sketch/auth/accounts/UsersManagerSpec.scala).


### 2.3 Permission Rules

**Access Rules**:

| Role / Relationship | Access To Entities                       |
|---------------------|------------------------------------------|
| **Admin**           | ✅ **All entities** (bypasses ownership) |
| **Superuser**       | ✅ All entities **except UserEntity**, which they can only access if they are the owner |
| **Owner**           | ✅ Only entities they **own**            |

**Creation Rules**:

| Entity Creation      | Rules                                          |
|----------------------|------------------------------------------------|
| **All** entities     | User must be **authenticated**                 |
| **Restrictions**     | None yet. See **Section 5. Future Directions** |

### 2.4 Privileges & Current Limitations

Right now, **access implies full permission**:
  * If a user can access an entity, they **can read, edit and delete** it.

This will change, as we introduce, for example:
  * Contributors with edit-only rights
  * Limit on the number of entities a user can create.


## 3. Future Directions

These are planned features:
* Fine-grained Permissions
* Contributors
* Attribute-Based Access Control.

| Feature                        | Description                             | Purpose                            |
|--------------------------------|-----------------------------------------|------------------------------------|
| Fine-Grained Permissions       | Specific privileges: `can view`, `can edit`, `can delete` | Avoid all-or-nothing permissions |
| Contributors                   | Users who cannot fully control entities | Needed for collaborative workflows |
| Attribute-Based Access Control | Restricted access by attributes like location, time or resource limits | Example: to limit the number of projects a user can create |


## 4. Implementation Reference

The authorisation rules provided in this document are defined by the `AccessControl` algebra, and implemented by `PostgresAccessControl`. The logic is thoroughly tested with property-based tests, and they serve as living specs.

| Category                       | Component                  | Specification                  |
|--------------------------------|----------------------------|--------------------------------|
| Authorisation Flow             | [PostgresAccessControlSpec](../storage/src/it/scala/org/fiume/sketch/storage/authorisation/postgres/PostgresAccessControlSpec.scala) | The reference implementation for [AccessControl](../shared-access-control/src/main/scala/org/fiume/sketch/shared/authorisation/AccessControl.scala) |
| Business component integration | [DocumentsRoutesSpec](../service/src/test/scala/org/fiume/sketch/http/DocumentsRoutesSpec.scala) | Ensures only authorised users have access to documents |


> ⚠️ Access control tests in `DocumentsRoutesSpec` and similar components might be moved to a dedicated spec in the near future, e.g. `DocumentsRoutesAccessControlSpec`.


## 5. References

* [Role-Based Access Control (RBAC)](https://en.wikipedia.org/wiki/Role-based_access_control)
* [Relationship-Based Access Control (ReBAC)](https://en.wikipedia.org/wiki/Relationship-based_access_control)
* [Attribute-Based Access Control (ABAC)](https://en.wikipedia.org/wiki/Attribute-based_access_control)
 