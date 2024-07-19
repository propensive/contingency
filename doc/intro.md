__Contingency__ is an experimental library for abstracting over error handling
strategies. In particular, it gives developers a choice between throwing
exceptions, returning errors in a variety of datatypes, and accumulating
several validation-style errors. Code must be written to accomodate
Contingency's generic error handling, but the changes from exception-throwing
code are trivial.
