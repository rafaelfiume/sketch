# Authentication

Defines how our systems authenticate users, manage sessions, and handle account deletion, ensuring security, scalability and compliance.


---

**Table of Contents**

1. [Goals](#1-goals)
2. [Overview](#2-overview)
3. [Authentication Flow](#3-authentication-flow)
    - 3.1 [Session Verification](#31-session-verification)
    - 3.2 [Account Creation](#32-account-creation)
4. [Algorithms & Configuration](#4-algorithms--configuration)
    - 4.1 [Password Hashing (BCrypt)](#41-password-hashing-bcrypt)
    - 4.2 [Session Tokens (Jwt + ECDSA P256)](#42-session-tokens-jwt--ecdsa-p-256)
5. [Security Best Practices](#5-security-best-practices)
6. [Account Lifecycle & Deletion](#6-account-lifecycle--deletion)
7. [Common Pitfalls & Tradeoffs](#7-common-pitfalls--tradeoffs)
8. [References](#8-references)


## 1. Goals

The authentication system must:
 * Securely **store and verify passwords**
 * Issue **stateless tokens** for authentication at scale
 * Provide a clear **user data lifecycle** (soft and hard deletion).


## 2. Overview

 The auth module uses a **hash-based authentication** for password security and **JWTs** (Json Web Tokens, pronounced as "jot") for session management.

| Component            | Purpose                                  | Implementation                    |
|----------------------|------------------------------------------|-----------------------------------|
| Password Storage     | Securely hash and verify user passwords  | `BCrypt` (built-in salt support)  |
| Session Token        | Issue and validate authentication tokens | `ECDSA P-256` for JWT signature   |
| Lifecycle Manager    | Increases user's data protection         | Scheduled deletion job            |


## 3. Authentication Flow

### 3.1 Session Verification

```
[User submits credentials]
        ↓
[Server hashes + verifies password with BCrypt]
        ↓
[If valid -> Issue signed Jwt with ECDSA P-256]
        ↓
[Client stores Jwt securely]
        ↓
[Subsequent requests include `Authorization: Bearer <token>]
        ↓
[Server verifies Jwt signature and claims]
        ↓
[Access granted or denied]
```

### 3.2 Account Creation

Triggered by an Admin using [UsersScript](src/main/scala/org/fiume/sketch/auth/scripts/UsersScript.scala). Account creation is handled by [UsersManager](src/main/scala/org/fiume/sketch/auth/accounts/UsersManager.scala). 

(Flow below excludes authorisation tasks for simplicity.)

```
[Script validates and sends `username` and `password`]
         ↓
[Server generates unique salt]
         ↓
[Hash password using BCrypt + salt]
         ↓
[Create account entry with user credentials]
         ↓
[Securely store user credentials]
```

## 4. Algorithms & Configuration

### 4.1 Password Hashing (BCrypt)

* **Brute-force atack resistant** via tunnable computational cost
* **Unique salts** prevents [Rainbow Table atacks](https://en.wikipedia.org/wiki/Rainbow_table).
* Adjustable cost factor - default: `12`. See [HashedPassword](../shared-auth/src/main/scala/org/fiume/sketch/shared/auth/Passwords.scala).

BCrypt format comprises **algorithm id**, **cost**, **unique salt** and **hash**:

    $2a$12$<22-character-salt><31-character-hash> 

Example: 

    $2a$12$phweZc5XDA7VGMoYQaI9pOeWhT3zuuo0.gPnIM07vn9AMZjXSFebq

### 4.2 Session Tokens (Jwt + ECDSA P-256)

* Stateless: no server-side session store required
* Self-contained: all claims needed for authorisation included.

##### Jwt Claims

| Claim              | Purpose                                    |
|--------------------|--------------------------------------------|
| sub                | User UUID                                  |
| iat                | Token issue time                           |
| exp                | Token expiration timestamp                 |
| preferred_username | Name in which a user identifies themselves |

##### ECDSA (Elliptic Curve Digital Signature Algorithm) P-256

* Popular choice for high-scale Jwt signing
* Smaller signatures than RSA with equivalent security
* Balance between safety and performance.

## 5. Security Best Practices

| Practice                  | Motivation                                               |
|---------------------------|----------------------------------------------------------|
| Unique Password Salts     | Prevents pre-computed hash attacks                       |
| Short Token Expiration    | Limits damage if a token is leaked                       |
| Periodic Key Rotation     | Limits impact of compromised signing keys                |
| Secure Key Storage        | Protects private signing keys from breaches              |


## 6. Account Lifecycle & Deletion

There is a two-stage deletion process to balance user privacy, compliance and account recovery.

Soft Deletion:
  * Marks account as deleted **without removing data**
  * Allows recovery within a **configurable grace-period**
  * User **cannot log in** during this period.

Hard Deletion:
  * **Permanent removal** of account and user-related data
  * Triggered automatically by a **scheduled job** after the grace period
  * Cascades through credentials, roles, and related user data.

##### Deletion Permissions

| Role    | Capability                              |
|---------|-----------------------------------------|
| Owner   | Soft-delete **own account**             |
| Admin   | Soft-delete **any account**             |


## 7. Common Pitfalls & Tradeoffs

**Avoid UNIQUE Constraints on Stored Hashes ❌:**

* BCrypt hashes are always unique (built-in salts)
* It could lead to migration issues when changing costs or algorithm
* It might create a false sense of password reuse prevention.

**Prevent Same Password Functionality:**

* Depends on policy/business rules
* Must be enforced at application layer
  - Example: deriving a deduplication key with HMAC
* Currently not implemented by this system.


## 8. References

* [Jwt](https://www.jwt.io/introduction)
* [BCrypt](https://en.wikipedia.org/wiki/Bcrypt)
* [ECDSA](https://en.wikipedia.org/wiki/Elliptic_Curve_Digital_Signature_Algorithm)
