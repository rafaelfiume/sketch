


### Account deletion - Missing steps:

1) Delete data from access-control module/service

1) Separate schema for event-bus


### Others

- TODO Reconcile UserCredentials and Account (do I need both? likely not)
- TODO POST /documents returns 201 with a Location header with a link to the newly created resource instead.

- Consider the Type State technique, to encode the state of a component using the type system.
 It can be helpful when a set of operations can be invoked on a type depending on its state.
 See https://www.youtube.com/watch?v=bnnacleqg6k at around 33m.

- Infrastructure: Consider https://render.com/


## Contextual Attributes
-
-There are attributes that dependent on context and don't belong to the core domain model
-of the domain ADTs themselves. I.e. they are not instrinsic properties of the entities, but arise -
-for example - from the relationship between a user and the entity (user roles and permissions).
-
-These external or contextual properties should be modelled separately from the core domain model
-to achieve separation of concerns.
-
-Ideas to model contextual attributes:
-
-```
-case class ContextualAttributes(
-  roles: Role = Role.Contextual(Owner),
-  lastAccessed: Option[String] = None,
-  isFavorited: Boolean = false
-)
-```
-
-An entity view, like `DocumentResponsePayload` would then include an extra field `contextualAttributes`.
-
-There could be a Typeclass defined to generate a view model enriched with contextualAttributes:
-
-```
-trait ContextualEnrichment[T]:
-  def enrich(domain: D, contextualAttributes: Map[String, Any]): V
-
-def enrichDomainToView[D, V](domain: D, contextualAttributes: Map[String, Any])(using ev: ContextualEnrichment[D, V]): V =
-  ev.enrich(domain, contextualAttributes)
-```
-

## Courses

- [Real Analyses](https://www.youtube.com/watch?v=RzSp9nIFnbo)

- [Big Picture of Calculus](https://www.youtube.com/watch?v=UcWsDwg1XwM&list=PLBE9407EA64E2C318&index=3)

- [A Vision of Linear Algebra](https://www.youtube.com/playlist?list=PLUl4u3cNGP61iQEFiWLE21EJCxwmWvvek)

- [Introduction to Functional Analyses](https://www.youtube.com/playlist?list=PLUl4u3cNGP63micsJp_--fRAjZXPrQzW_)

- [Introduction to Algorithms](https://www.youtube.com/watch?v=HtSuA80QTyo&list=PLUl4u3cNGP61Oq3tWYp6V_F-5jb5L2iHb&index=3)

- [Introduction to Algorithms, Spring 2020](https://www.youtube.com/playlist?list=PLUl4u3cNGP63EdVPNLG3ToM6LaEUuStEY)

- [Design and Analysis of Algorithms, Spring 2015](https://www.youtube.com/playlist?list=PLUl4u3cNGP6317WaSNfmCvGym2ucw3oGp)

- [Introduction to Computational Thinking and Data Science, Fall 2016](https://www.youtube.com/playlist?list=PLUl4u3cNGP619EG1wp0kT-7rDE_Az5TNd)
