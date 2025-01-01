


### Account deativation/deletion - Missing steps:

1) Clean up all entities upon permanent deletion

 . . . steps coming soon


 - Create the events table
 - Query to select and lock an event for processing:




### Others

- TODO POST /documents returns 201 with a Location header with a link to the newly created resource instead.

- Consider the Type State technique, to encode the state of a component using the type system.
 It can be helpful when a set of operations can be invoked on a type depending on its state.
 See https://www.youtube.com/watch?v=bnnacleqg6k at around 33m.
