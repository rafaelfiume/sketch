# Sketch

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/rafaelfiume/sketch/tree/main.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/rafaelfiume/sketch/tree/main) [<img src="https://img.shields.io/badge/dockerhub-images-blue.svg?logo=LOGO">](<https://hub.docker.com/repository/docker/rafaelfiume/sketch/general>)


**Table of Contents**

1. [Project Philosophy](#1-project-philosophy)
2. [Start Here](#2-start-here)
3. [Architecture](#3-architecture)
    - 3.1 [Domain Modules](#31-domain-modules)
      - [Identity](#identity)
      - [Access Control](#access-control)
      - [Projects](#projects)
    - 3.2 [Application Layers](#32-application-layers)
4. [DevOps Practices](#4-devops-practices)
    - 4.1 [Continuous Integration](#41-continuous-integration)
    - 4.2 [12-Factor Principles](#42-the-12-factor-principles)
    - 4.3 [Scripting Guidelines](#43-scripting-guidelines)
    - 4.4 [Future Directions](#44-future-directions)
5. [From Ivory Tower to Production Code](#5-from-ivory-tower-to-production-code)
6. [Further Reading](#6-further-reading)

## 1. Project Philosophy

Sketch is built on the principle of conceptual alchemy: **transforming abstract theory into practical engineering solutions**. 

This backend template uses non-obvious ideas, like using Information Theory to [write clear docs](docs/best-practices/Documentation.md) and [Category Theory](docs/best-practices/Applied-Theory.md#4-mathematical-foundations-category-theory---safe--composable-components) for building predictable and composable software components.

These diverse theories are applied not for their own sake, but to craft solutions to real-world problems. The result is a product that is secure, maintainable and scalable.

> See the section [From Ivory Tower to Production Code](#5-from-ivory-tower-to-production-code) for examples of this philosophy in action.


## 2. Start Here

To quickly run Sketch and its dependencies on your local machine, follow the [Onboarding](docs/start-here/Onboarding.md) guide.


## 3. Architecture

Sketch is a relatively small **modular monolith**. It's a single deployable unit, organised into modules, each composed by layers, for [low coupling](https://en.wikipedia.org/wiki/Coupling_(computer_programming)) and [high cohesion](https://en.wikipedia.org/wiki/Cohesion_(computer_science)).

This design provides two key benefits:
  1. **Fast iteration** during early and mid project stages
  2. A clear **migration path to a microservice architecture** as scaling challenges emerge.

### 3.1 Domain Modules

A domain module represents a distinct and cohesive set of business responsibilities.
Each can be evolved or extracted to a microservice independently later. They:
  * Encapsulate their data and internal implementation
  * Expose functionality through APIs (algebras) with well-defined behaviour.

The following modules compose the current system:

| **Module**            | Purpose                   | Scope              | Future Directions      |
------------------------|---------------------------|--------------------|------------------------|
| **[Identity](auth/README.md)** | Identifies users and manages their account lifecycle | Handles user registration and authentication | Profile management |
| **[Access Control](shared-access-control/README.md)** | Defines and enforces access-control policies and rules | Provides role- and owner-based authorisation mechanisms across modules | Fine-grained permissions to move beyond all-or-nothing access |
| **Projects** | Project lifecycle management | Securely handles user documents | - Expand to full lifecycle management, from early business opportunities to live operations<br><br> - Provide actionable business insights, such development opportunities and ROI |

### 3.2 Application Layers

Layers provide a clear separation of concerns, ensuring the system remains adaptable in a fast-evolving environment.


| Layer                       | Responsibility             | Components             | Dependencies             |  Do Not Allow |
|-----------------------------|----------------------------|------------------------|--------------------------|---------------|
| **Inbound Adapters**        | Entry points into the application.<br><br>Convert external inputs into a format the application layer understands, thus isolating the core domain | - [**Http APIs**](/docs/architecture/inbound-adapters/http/Design.md). E.g. [UserRoutes](/auth/src/main/scala/org/fiume/sketch/auth/http/UsersRoutes.scala)<br>- **Event Consumers**<br>- **CLI commands**<br>- **gRPC** | Application / Domain (entities) | **✗** Exposed domain entities to external world (use DTOs)<br><br> **✗**  Business-logic<br><br> **✗**  DAOs or external APIs used directly |
| **Application Layer**       | Implements use-cases by orchestrating domain components and invoking external modules through ports.<br><br>Defines transaction boundaries | E.g. [UsersManager](/auth/src/main/scala/org/fiume/sketch/auth/accounts/UsersManager.scala) | Domain (entities, ports) | **✗**  Bypass business rules<br><br> **✗**  Direct infrastructure access (e.g. low-level transaction code) |
| **Domain Layer**            | The core value of the system.<br><br> Expresses the business model, rules, and ports (contracts) for required external capabilities | - **Entities**. E.g. [User](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/User.scala)<br>- **Ports**. E.g. [UsersStore](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/algebras/UsersStore.scala) | None | **✗**  Dependencies on any other layer |
| **Outbound Adapters** (Infrastructure) | Implements ports, persisting the domain state, or calling external APIs | - **DAO**. E.g. [PostgresUsersStore](/storage/src/main/scala/org/fiume/sketch/storage/auth/postgres/PostgresUsersStore.scala)<br> - **Event Producers** | Domain (ports; entities as inputs/outputs) | **✗** Business rules<br><br> **✗**  Leaking infrastructure details |

**Compile-time dependencies:**

```
[ Inbound Adapters ] -> [ Application ] -> [ Domain ] <- [ Infrastructure ]

```


## 4. DevOps Practices

This project uses DevOps practices to automate releases and operational tasks, improve collaboration and build a cloud-ready, scalable application.

The goal is to **increase release cadence with confidence** through [shift-left testing](https://en.wikipedia.org/wiki/Shift-left_testing) and reliable operations.

### 4.1 Continuous Integration

The [CI pipeline](https://app.circleci.com/pipelines/github/rafaelfiume/sketch) automatically builds, tests, versions and [publishes](https://hub.docker.com/repository/docker/rafaelfiume/sketch) an image of the newest application version whenever changes are committed.
This ensures repeatable and frequent releases, preventing the pain of massive and infrequent release cycles. 

> **Note:** Continuous Delivery (CD) will expand this process to automatically deploy new versions to production.

See [Releases documentation](docs/devops/Releases.md).

### 4.2 The 12-Factor Principles

Applying the 12-Factors helps ensure the application is **portable**, **cloud-native** and **scalable**. 

Current focus includes [Admin Processes](/docs/devops/Admin.md),
with future guides planned for other principles, such as processes and logs.

### 4.3 Scripting Guidelines

Scripts **automate repetitive tasks** and work as **executable documentation**, with precise instructions to perform tasks, from starting the application stack to automating the release pipeline.

See the [Scripting Guidelines](docs/devops/Scripting.md) for tips on writing effective and maintainable scripts.

### 4.4 Future Directions

The plan is to expand DevOps practices in these areas:
  * **Infrastructure as Code (IaC)**: Declarative infrastructure provisioning with Terraform
  * **Continuous Delivery (CD)**: Automatic release to production after successful CI build
  * **Monitoring & Observability**: Metrics, logs and tracing with Prometheus, Grafana and Jaeger
  * **Security Practices**: Integrated security scanning and solid secrets management.

```
[ CI ] -> [IaC] -> [ CD ] -> [ Observability ] -> [ Security Practices ]
```


## 5. From Ivory Tower to Production Code

⚡ See [From Ivory Tower to Production Code](docs/best-practices/Applied-Theory.md) for a broader collection of theory-to-practice insights.



## 6. Further Reading

### 6.1 General Guidelines

* [Documentation Guidelines](/docs/best-practices/Documentation.md) - Write concise, well-structured docs inspired by Information Theory principles.

### 6.2 Architecture

* [Hexagonal Architecture](https://en.wikipedia.org/wiki/Hexagonal_architecture_(software)): Inspiration for inbound/outbound adapters design and implementation guidelines
* [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html): Informs the project architectural layers and dependencies rules
* [HTTP Inbound Adapters - Design Guidelines](/docs/architecture/inbound-adapters/http/Design.md): Design effective and maintainable HTTP APIs.
* [Domain Layer - Design Guidelines](/docs/architecture/domain/Design.md): Model business domains using DDD principles
* [Application Layer - Design Guidelines](/docs/architecture/application/Design.md): Orchestrate domain objects to perform business workflows
* [Datastore Outbound Adapters - Design Guidelines](/docs/architecture/outbound-adapters/datastore/Design.md): Implement domain-defined storage capabilities without leaking infrastructure details into the domain or application layers.


### 6.3 DevOps

* [Admin Processes (The 12th Factor)](/docs/devops/Admin.md) - Build and maintain admin/management scripts
* [Release Guidelines](/docs/devops/Releases.md) - Learn about the project CI pipeline and release process
* [Scripting Guidelines](/docs/devops/Scripting.md) - Write robust CLI apps and scripts

### 6.4 Local Development

* [Onboarding](/docs/start-here/Onboarding.md) - Run the application stack in your machine
* [Dev Environment Troubleshooting](/docs/start-here/Local-Troubleshooting.md) - Solve common local development issues
