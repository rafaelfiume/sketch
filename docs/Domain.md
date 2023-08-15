# Domain

## Documents

Documents are a core domain concept as many workflows are supported or require them.
They come in many forms: images, texts, ids, and are considered extremelly sensitive.

### Metadata

Document metadata is anything that is not the content of the document itself, for instance `name` and `description`.
Future metadata might include `owner`, `workflow`, `fileSize`, `fileType`, `fileHash`, `fileLocation`, `fileFormat`, `fileEncoding`, `fileCompression`, etc.

### Ownership (proposal)

Documents should have one owner, which is the person responsible for it.

A) The simplest one that works:
 * A document is owned by a single user
 * A user can own many documents
 * A user can be the person uploading the document (i.e authenticated by the system) or the client of the business (i.e not authenticated by the system)

### Workflow (proposal)

If documents are sensitive, there must be a reason for storing it. The reason should be a workflow that requires it.
