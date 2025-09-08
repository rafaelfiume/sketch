# Documentation Guidelines

## 1. Goal 

Write effective, high signal-to-noise ratio, low-entropy technical documentation for rapid comprehension of relevant information by applying Information Theory concepts:

* Maximise Signal: only relevant information
* Minimise Noise: less confusion and clutter
* Low Entropy: predictable & structured content.

This guide compares the [Authentication Guide](../../auth/README.md) with its [previous version](https://github.com/rafaelfiume/sketch/blob/c7604e84843a07c6cba269adadb7b911c8558baa/docs/Auth.md).


## 2. Core Principles

| Principle               | Description                                                          | Objective                  |
|-------------------------|----------------------------------------------------------------------|--------------------------- |
| Maximise Signal         | Content directly helps reader understand or maintain the system      | Reduces cognitive overload; reader quickly find relevant info |
| Minimise Noise          | Avoid tangential explanations and redundancy                         | Prevents confusion; keeps doc focused |
| Reduce Entropy          | Intuitive structure, consistent formatting and terminology           | Reader can build mental model faster |   
| Progressive Disclosure  | High-level concepts first, deep dive into details later              | Avoids overwhelming readers; supports people with different skills |
| Actionable Reference    | Provides concrete and unambiguous actions, links to specs, codebase  | Reader can dive deeper without cluttering the doc |


## 3. Information Density: Signal-to-Noise Ratio (SNR)

SNR measures the amount of relevant information (signal) compared to irrelevant or distracting content (noise).

| High Signal ✅                                              | High Noise ❌                |
| ------------------------------------------------------------| -----------------------------------|
| System-specific info, brief definitions, references to code | General theory, long background |
| Links to detailed resources                                 | Forcing to read long paragraphs for context |
| Tables for quick scanning & short and precise bullets       | Long paragraphs                   |
| Diagrams for flow                                           | Text-only processes descriptions  |

#### Bad: Low SNR

From the [old authentication doc](https://github.com/rafaelfiume/sketch/blob/c7604e84843a07c6cba269adadb7b911c8558baa/docs/Auth.md):

    "JWTs consist of three parts: a header, a payload (claims), and a signature. The payload can contain information about the user, their roles, and other relevant data. JWTs are commonly used for authentication and authorization in web applications..."

This is useful general theory, but it **doesn't help to maintain a specific system** -> _noise_.

#### Good: High SNR

Compare with:

    " The auth module uses a **hash-based authentication** for password security and **JWTs** (Json Web Tokens, pronounced as "jot") for session management."

This is direct and relevant signal with no fluff: it tells the mechanisms used and why in under two lines.


## 4. Reduce Entropy

Entropy is the **degree of disorder or unpredictability** in how information is structured.
High entropy makes a documentation harder to scan and increases cognitive load (readers will have to spend extra mental energy to process disorganised information).

A **predictable structure** lowers entropy, making the document easier to navigate.

| Low Entropy ✅                                | High Entropy ❌                |
| ----------------------------------------------| -----------------------------------|
| Clear scope for each section                  | Mixed concepts with no clear order  |
| Easy to reference specific sections           | Hard to pinpoint specific topics   |

#### Bad: High SNR, but High Entropy

Excerpt from the old [Strategy](https://github.com/rafaelfiume/sketch/blob/c7604e84843a07c6cba269adadb7b911c8558baa/docs/Auth.md#strategy) section:

    "Hash-based authentication and JSON Web Tokens (JWT) serve different purposes and should be used together. Hash-based authentication securely stores passwords, while JWT transmits authentication data. In sketch's authentication system, passwords are verified first, then a JWT is generated and included in subsequent requests..."

All the content is relevant, but too many concepts (hashing, JWT, salt) packed together without clear boundaries.
This forces the reader to digest the information, a classic symptom of high entropy.

#### Good: Predictable, Low Entropy

Numbered sections:

    1. Goals -> 2. Overview -> 3. Flows -> 4. Algorithms -> 5. Security -> 6. Lifecycle -> 7. Pitfalls -> 8. References

This makes the document predictable, enabling readers to jump to relevant sections and reducing cognitive overload.


## 5. Progressive Disclosure

Introduce concepts in **layers**, starting with a high-level view and gradually revealing details.
This prevents the reader from being overwhelmed and lets them **naturally zoom in as they progress**.

#### Example - Authentication Doc:

| Layer                          | [Final Doc](../../auth/README.md) ✅                | [Previous Doc](https://github.com/rafaelfiume/sketch/blob/c7604e84843a07c6cba269adadb7b911c8558baa/docs/Auth.md) ❌  |
| -------------------------------| ----------------------------------------------------| ------------------------------------------------|
| High-level context             | Section 1 - Goals                                   | Missing entirely                                |
| Overview                       | Section 2 - Concise, table for quick scan           | Buried inside paragraphs                        |
| Flow                           | Section 3 - ASCII diagrams                          | Scattered narrative steps                       |
| Technical details              | Section 4-5 - Algorithms & Best practices           | Spread throughout the document                  |
| Policies / lifecycle           | Section 6 - Concise points and table                | Text heavy, hard to scan                        |
| Edge cases & pitfalls          | Section 7 - Clearly separated                       | Mixed into salt discussion                      |
| References & external links    | Section 8 - External ressources & links to codebase | Basic Jwt explained inline                      |

Takeaway: Gradually move from _context -> details -> related information -> optional references_, allowing the reader to find what they need without wading through irrelevant information.


## 6. Actionable References

Make the document **practical**, not just theoretical.
Enable readers to take **clear next steps**.

|  [Final Doc](../../auth/README.md) ✅                          | [Previous Doc](https://github.com/rafaelfiume/sketch/blob/c7604e84843a07c6cba269adadb7b911c8558baa/docs/Auth.md) ❌  | 
|----------------------------------------------------------------|----------------------------------------------------|
| Clear warnings: _Avoid UNIQUE Constraints on Stored Hashes ❌_ | Vague allusion to collisions, no clear warning |
| Direct links to code: [UsersScript](../../auth/src/main/scala/org/fiume/sketch/auth/scripts/UsersScript.scala) | No mentions of scripts or links |
| External links for deep-diving: JWT, BCrypt, ECDSA             | Explains JWT basics inline                     |

Takeaway: Inlining basic theory increases noise. Use link to external resources instead.


## Checklist

Before completing a documentation, ask yourself:

* [ ] Does it start with a **clear goal** and **concise overview**?
* [ ] Would ASCII diagrams help describing **key flows**?
* [ ] Is all content **system-specific**?
* [ ] Can a teammate **scan and understand the document in 5 minutes**?
* [ ] Are **warnings and limitations clearly defined**?
* [ ] Are there **external links** for basic theory and in-depth exploration?
