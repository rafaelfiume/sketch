# Authentication

## Strategy

Hash-based authentication and JSON Web Tokens ([JWT](https://jwt.io)) serve different purposes and should be used together in the authentication system.

Hash-based authentication refers to the process of securely hashing and storing user passwords on the server side. It ensures that the passwords are not stored in plain text and helps protect against unauthorized access even if the stored password hashes are compromised. Hash-based authentication is typically used as a means of verifying the user's identity during the authentication process.

On the other hand, JWT is a token-based authentication mechanism that provides a secure and self-contained way to transmit authentication and authorization data between parties. JWTs are commonly used for authentication and authorization in web applications and APIs. They consist of three parts: a header, a payload (claims), and a signature. The payload can contain information about the user, their roles, and other relevant data.

In an authentication system, you can combine hash-based authentication with JWTs to achieve a morecomprehensive solution:

1. Hash-based authentication can handle the initial verification of the user's identity. Upon successful verification of the username and password (after applying the necessary hashing and salting techniques), the server can generate a JWT as a form of authentication token.

2. The JWT can then be issued to the client, which can include it in subsequent requests as an "Authorization" header with the "Bearer" authentication scheme.

3. On the server side, validate the JWT's signature to ensure its authenticity and integrity. You can also extract and verify the claims within the payload to determine the user's identity and access privileges.

### Hash-based Authentication and Sault

When implementing hash-based authentication, it is generally recommended to include a salt column in the users table. The salt is an additional random value that is combined with the user's password before hashing it. It adds an extra layer of security to the password storage and helps protect against pre-computed or rainbow table attacks. Besides, The salt makes each user's hashed password unique, even if they have the same password.

Including a salt column in the users table allows you to generate a unique salt value for each user during the registration process. When the user attempts to authenticate, you retrieve their salt from the table, combine it with their provided password, and then hash the result for comparison against the stored password hash.






Note:

https://github.com/jwt-scala/jwt-scala
libraryDependencies += "com.github.jwt-scala" %% "jwt-circe" % "9.4.0

RS256 to encode jwt?

