# Datastore - Design (Draft)

Despite of the disadvantages of storing binaries (images, pdf's, etc) in a database,
we are doing that anyway for the following reasons:

 * Easier implementation, makes it a good fit for a MVP: e.g. no need to keep pointers
 * We don't expect performance problems associated with binaries stored in the db: not too many, nor huge bins expected
 * Easier to keep them secure
