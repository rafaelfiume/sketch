# Authentication

## Strategy

Hash-based authentication and JSON Web Tokens ([JWT](https://jwt.io)) serve different purposes and should be used together in the authentication system.

Hash-based authentication refers to the process of securely hashing and storing user passwords on the server side. It ensures that the passwords are not stored in plain text and helps protect against unauthorized access even if the stored password hashes are compromised. Hash-based authentication is typically used as a means of verifying the user's identity during the authentication process.

On the other hand, JWT is a token-based authentication mechanism that provides a secure and self-contained way to transmit authentication and authorization data between parties. JWTs are commonly used for authentication and authorization in web applications and APIs. They consist of three parts: a header, a payload (claims), and a signature. The payload can contain information about the user, their roles, and other relevant data.

In sketch's authentication system, hash-based authentication is combined with JWTs to achieve a more comprehensive solution:

1. Hash-based authentication can handle the initial verification of the user's identity. Upon successful verification of the username and password (after applying the necessary hashing and salting techniques), the server can generate a JWT as a form of authentication token.

2. The JWT can then be issued to the client, which can include it in subsequent requests as an "Authorization" header with the "Bearer" authentication scheme.

3. On the server side, validate the JWT's signature to ensure its authenticity and integrity. It also extracts and verifies the claims within the payload to determine the user's identity and access privileges.

### Algorithms

* BCrypt: Hashing algorithm for password hashing.
* ECDSA (Elliptic Curve Digital Signature Algorithm) with P-256: Digital signature algorithm for JWT signing and verification.

### Hash-based Authentication

#### Salt

Salt is an additional random value that is combined with the user's password before hashing it. It adds an extra layer of security to the password storage and unique salts help to protect against pre-computed or rainbow table attacks. Besides, salt makes each user's hashed password unique, even if they have the same password.

Note that BCrypt includes the salt in the hashed password and thus doesn't require salt when verifying password.
Including a salt column in the users table allows unique salt value for each user during the registration process and increased flexibility if changing hash algorithm.

#### Hashed Password and Salt Uniqueness

Enforcing salt uniqueness prevents precomputed hash attacks. Note that salt should be retained during updates.

However, enforcing hashed password uniqueness can cause collisions (e.g. when migrating to a new hash algorithm) and cause performance issues (?).

This hybrid approach tries to strike a balance between security and usability.

### Token-based authentication

Please, check [this](https://github.com/rafaelfiume/sketch/pull/111) out for more details on Jwt token generation and verification.

## Account Deletion

1) An account is first marked to deletion (soft deletion over a specified period)
  1) Once that interval has passed (configurable), the user data should be permanently deleted
1) An account 'Owner' is able to mark his/her own account for deletion
1) An 'Admin' is able to mark any account for deletion
1) All data (entities) from user should be deleted too upon permanent deletion,
including authorisation/access_control data.
