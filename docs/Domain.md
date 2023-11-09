# Domain

## Documents

Documents are a core domain concept as many workflows are supported by or require them.
They come in many forms: images, texts, ids, and are considered extremelly sensitive.

### Metadata

Document metadata are anything that not the content of the document itself, for instance `name` and `description`.
Future metadata might include `owner`, `workflow`, `fileSize`, `fileType`, `fileHash`, `fileLocation`, `fileFormat`, `fileEncoding`, `fileCompression`, etc.

### Authorship, Ownership & Workflows (proposal)

 * The person uploading the document (author) can be someone that represents the business, or a client of the business (owner).
 * If author and owner are not the same person, then the former represents the latter if document is part of a workflow.
 * The author must be authenticated by the system.

### Endpoints (proposal)

```
GET /documents?author=<user_uuid>          # All documents created by user
GET /documents?owner=<user_uuid>           # All documents owned by user
GET /documents?workflow=<workflow_uuid>    # All documents associated with a workflow
GET /documents                             # Returns 400
```

### Data Privacy

If documents are sensitive, there must be a reason for storing it. The reason should be a workflow that requires it.
There should be regular clean ups once a document is no longer needed.
A document 'no longer neeed' will be specific to the business.

```
+------------------------------------------------------------------------------------------------------------------------------------------------------------------+
+                                                                            Documents                                                                             +
+------------------------------------------------------------------------------------------------------------------------------------------------------------------+
+                  uuid                | ...  |                 author               |                 owner                |              workflow_id             |
+--------------------------------------+------+--------------------------------------+--------------------------------------+--------------------------------------+
| 1a7fd9a4-1a23-45c6-91a0-71c4c4d95036 | ...  | 7a21d5bc-9e3a-4c1b-bf6c-33cc9926c3ac | 7a21d5bc-9e3a-4c1b-bf6c-33cc9926c3ac | 4b2a3091-7d2e-46f8-a4eb-25bc4ff695c6 |
| f4e3b2a0-8f6c-43b2-9a9e-34d82a48b22d | ...  | 7a21d5bc-9e3a-4c1b-bf6c-33cc9926c3ac | 9f7d71c5-d8d0-49e3-913d-59b0a5d5d20c | 4b2a3091-7d2e-46f8-a4eb-25bc4ff695c6 |
| 8dfc3bc0-0b89-4e92-8baf-e5e04e2b2a8d | ...  | 3b21d5bc-2f8e-41d2-af0c-72cc8826c3bc | 3b21d5bc-2f8e-41d2-af0c-72cc8826c3bc | NULL                                 |
| a2e1bce4-9a2c-4c67-b89a-1c8c4c2c98a4 | ...  | 3b21d5bc-2f8e-41d2-af0c-72cc8826c3bc | 7a21d5bc-9e3a-4c1b-bf6c-33cc9926c3ac | 4b2a3091-7d2e-46f8-a4eb-25bc4ff695c6 |
+--------------------------------------+------+--------------------------------------+--------------------------------------+--------------------------------------+
```

Feito com ❤️ por Artigiani.
