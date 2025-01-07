


### Account deativation/deletion - Missing steps:

1) Clean up all entities upon permanent deletion

- Config recipients of deletion notifications dynamically
  - Define algebra and simple in-memory implementation of getRecipients config
  - Update the deletion job and test
  - Refactor config Api and impl to clearly distinguish between dynamic and env vars configs
  - Implement an implementation for dynamic config based on Postgres

- Services (e.g. sketch) deletes user data upon accound deletion

For the table....

```
Indexing: Add a GIN index on the config_value column for faster querying within JSONB structures:

CREATE INDEX idx_configs_value ON configs USING gin (config_value);
```



### Others

- TODO POST /documents returns 201 with a Location header with a link to the newly created resource instead.

- Consider the Type State technique, to encode the state of a component using the type system.
 It can be helpful when a set of operations can be invoked on a type depending on its state.
 See https://www.youtube.com/watch?v=bnnacleqg6k at around 33m.
