# Domain

## Entities

The domain is represented mainly by entities, aggregates (ADTs) and algebras.

There can be multiple domains, each one of them with their own components and abstractions. For example, a `User` (`authentication`, or simply `auth`) or a `Document` (`sketch`) are entities, a `Project` (`sketch`) is an aggregate and `AccessControl` (`authorisation`) is an algebra.

A useful entity and aggregate [definition](https://www.ibm.com/topics/microservices#Common+patterns) is:
```
An entity is an object distinguished by its identity. For example, on an e-commerce site, a product object might be distinguished by product name, type, and price. An aggregate is a collection of related entities that should be treated as one unit. So, for the e-commerce site, an order would be a collection (aggregate) of products (entities) ordered by a buyer. These patterns are used to classify data in meaningful ways.
```

## Contextual Attributes

There are attributes that dependent on context and don't belong to the core domain model
of the domain ADTs themselves. I.e. they are not instrinsic properties of the entities, but arise -
for example - from the relationship between a user and the entity (user roles and permissions).

These external or contextual properties should be modelled separately from the core domain model
to achieve separation of concerns.

Ideas to model contextual attributes:

```
case class ContextualAttributes(
  roles: Role = Role.Contextual(Owner),
  lastAccessed: Option[String] = None,
  isFavorited: Boolean = false
)
```

An entity view, like `DocumentResponsePayload` would then include an extra field `contextualAttributes`.

There could be a Typeclass defined to generate a view model enriched with contextualAttributes:

```
trait ContextualEnrichment[T]:
  def enrich(domain: D, contextualAttributes: Map[String, Any]): V

def enrichDomainToView[D, V](domain: D, contextualAttributes: Map[String, Any])(using ev: ContextualEnrichment[D, V]): V =
  ev.enrich(domain, contextualAttributes)
```

## Core Business Entities

### Documents

Documents are a core domain concept as many workflows are supported by or require them.
They come in many forms: images, texts, ids, and are considered extremelly sensitive.

#### Metadata

Document metadata are anything that not the content of the document itself, for instance `name` and `description`.
Future metadata might include `owner`, `workflow`, `fileSize`, `fileType`, `fileHash`, `fileLocation`, `fileFormat`, `fileEncoding`, `fileCompression`, etc.

#### Data Privacy

If documents are sensitive, there must be a reason for storing it. The reason should be a workflow that requires it.
There should be regular clean ups once a document is no longer needed.
A document 'no longer neeed' will be specific to the business.
