# Domain

## Entities

The domain is represented by entities. For instance, documents and projects.
There can be multiple domains, each one of them with their own entities. For example:
 * Authentication & Authorisation: `User`
 * Core business: `Document`.

## Contextual Attributes

There are attributes that dependent on context and don't belong to the core domain model
of the entities themselves. I.e. they are not instrinsic properties of the entities, but arise -
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
