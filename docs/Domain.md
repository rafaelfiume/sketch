# Domain

## Documents

Documents are a core domain concept as many workflows are supported by or require them.
They come in many forms: images, texts, ids, and are considered extremelly sensitive.

### Metadata

Document metadata are anything that not the content of the document itself, for instance `name` and `description`.
Future metadata might include `owner`, `workflow`, `fileSize`, `fileType`, `fileHash`, `fileLocation`, `fileFormat`, `fileEncoding`, `fileCompression`, etc.

### Ownership (proposal)

Documents should have one owner, which is the person or entity responsible for it:
 * A document is owned by a single entity
 * An entity can own many documents
 * An entiy can be the person uploading the document or the company tha runs the system
 * The person uploading the document can be someone that represents the business or a client of the business
 * The owner must be authenticated by the system.

### Workflow (proposal)

If documents are sensitive, there must be a reason for storing it. The reason should be a workflow that requires it.
