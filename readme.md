[<img alt="GitHub Workflow" src="https://img.shields.io/github/actions/workflow/status/propensive/contingency/main.yml?style=for-the-badge" height="24">](https://github.com/propensive/contingency/actions)
[<img src="https://img.shields.io/discord/633198088311537684?color=8899f7&label=DISCORD&style=for-the-badge" height="24">](https://discord.gg/MBUrkTgMnA)
<img src="/doc/images/github.png" valign="middle">

# Contingency

__Versatile error handling for every scenario.__

Contingency is an experimental library for abstracting over error handling
strategies. In particular, it gives developers a choice between throwing
exceptions, returning errors in a variety of datatypes, and accumulating
several validation-style errors. Code must be written to accomodate
Contingency's generic error handling, but the changes from exception-throwing
code are trivial.

## Features

- error are checked as _capabilities_
- choose global and localized strategies for error-handling
- fully-typesafe error handling
- selectively ignore errors considered "impossible"
- aggregate multiple errors, like a _validation_
- recover from specific errors with success values
- mitigate specific errors into more general errors


## Availability

Contingency has not yet been published. The medium-term plan is to build it with
[Fury](https://github.com/propensive/fury) and to publish it as a source build
on [Vent](https://github.com/propensive/vent). This will enable ordinary users
to write and build software which depends on Contingency.

Subsequently, Contingency will also be made available as a binary in the Maven
Central repository. This will enable users of other build tools to use it.

For the overeager, curious and impatient, see [building](#building).


## Getting Started

All Contingency terms and types are defined in the `contingency` package:
```scala
import contingency.*
```

_Contingency_ provides a number of different ways to work with errors in Scala,
for libraries which opt in to its advanced error-handling capabilities.

Contingency builds upon Scala 3's _safer exceptions_ with its new
`boundary`/`break` control flow syntax to abstract over exception handling.

We will look at how this works at the call site first, and then from the
implementor's perspective.

### Partial methods

A _partial method_ is a method which may not produce a result for certain
inputs, i.e. it is not _total_. Partial methods are already familiar in Scala
(and Java), but the way they handle the absence of a result is invariably to
throw an exception, determined directly or indirectly by the code in the
method's implementation. The method's signature _may_ also specify the types of
exception it can throw, if it has been written with _safer exceptions_ in mind.

For Contingency's purposes, a partial method is one which declares a `raises`
clause in its type, such as,
```scala
import fulminate.{msg, Error}
import rudiments.Bytes

case class ReadError() extends Error(msg"the data could not be read")

def readFromDisk(): Bytes raises ReadError =
  Bytes() // Needs implementation
```

The `raises ReadError` clause is equivalent to taking an additional contextual
type parameter of type `Raises[ReadError]`, like so,
```scala
def readFromDisk2()(using Raises[ReadError]): Bytes =
  Bytes() // Needs implementation
```
and this latter form is more typical for methods which may raise more than one
different type of error.

### Raising

Contingency introduces the terminology of _raising_ as a generalization of
_throwing_ which is dependent on callsite context. If the callsite context
calls for throwing, then _raising an error_ will mean _throwing an error_. But
there are alternative interpretations of raising which don't involve throwing.

Raising is modeled as a _capability_, represented by a `Raises` value that is
implicitly passed to the `readFromDisk` method. So the presence of a contextual
`Raises[ReadError]` instance indicates the _capability_ of raising a
`ReadError`.

Code which calls a partial method without contextual `Raises` instances of the
appropriate types to satisfy the `using` parameters of the method will be a
type error. Thus, it is impossible to call a partial method which needs the
capability to raise certain error types unless those capabilities exist in the
callsite context.

This principal is critical for robust and safe error handling, since it
leverages the type system to oblige the programmer to handle the exceptional
cases of partial methods.

### Simple Error Handling

If we just don't care about error handling, for example when building a
prototype, we can effectively `turn off` error handling by providing a global
contextual value which "handles" all errors by throwing them as exceptions,
```scala
import contingency.errorHandling.throwUnsafely
```
which is equivalent to Scala's default behavior: errors will be unchecked, and
will be thrown like traditional exceptions, bubbling through the stack until
caught in a `catch` block, or reaching the bottom of the stack.

A similar, but more fine-grained use case applies if we know that (or at least
make an informed judgement that) a call to a partial method will definitely
return a value, and we decide that there is no point in handling an error case
that will not happen in practice.

Such an expression or block of statements can be wrapped in a call to
`unsafely`, which will provide a general `Raises` instance necessary for
compilation, like `errorHandling.throwUnsafely`, but expected to never be used,
and constrained to just that expression or those statements.

```scala
@main
def run(): Unit = unsafely:
  val bytes = readFromDisk()
  Out.println(bytes.length)
```

Similar to `unsafely` is `safely`, which can also wrap expressions or
statements. But unlike `unsafely`, which can throw exceptions, `safely` will
return an `Unset` value if an error is raised. This effectively turns any
expression which would return some `ReturnType` into an expression which
returns `Optional[ReturnType]`.

One happy benefit of this is that the relatively expensive performance cost of
constructing and throwing an exception, only to discard it for an `Unset`
value, is saved. The exception instance is never constructed because Contingency
knows from the callsite context (i.e. inside the `safely` wrapper) that it can
just return `Unset` instead.

### Mitigation

It is good practice for methods which do different things to raise different
types of error. It makes it much easier to diagnose a problem when the error's
type gives a strong indication about what went wrong.

It also encourages composition or sequencing of partial methods, confident that
an error raised in the resultant expression or block will carry enough
information to disambiguate it from other possible errors in the same code.

This compositionality is helpful until we reach a method boundary where we need
to declare every possible error that could be raised from that method call.

Indeed, if we were defining a partial method called `publish` which writes to
disk, invokes a shell command, and then sends an HTTP request, it would be
frustrating for users of that method to have to handle `WriteError`s,
`ShellError`s and `HttpError`s when they are interested primarily in knowing
that _publishing_ failed, and only secondarily in the underlying cause.

So rather than defining it as,
```scala
def publish(): Unit raises WriteError raises ShellError raises HttpError =
  write()
  invoke()
  sendRequest()
```
we would prefer to introdue a new error type, `PublishError`, containing the
detail of the issue, like so:
```scala
enum PublishIssue:
  case Disk, Shell, Internet

case class PublishError(cause: PublishIssue)
extends Error(msg"publishing failed because of $cause")

def publish2(): Unit raises PublishError = ???
```

However, the calls to `write()`, `invoke()` and `sendRequest()` each raise
different types of error, and the body of `publish2` only has the capability of
raising `PublishError`s, by virtue of its `Unit raises PublishError` return
type.

The solution is to introduce _mitigations_.

A mitigation is a given instance which transforms an error of one type into an error of a different type. In the example above, we need mitigations to transform `WriteError`s, `ShellError`s and `HttpError`s into `PublishError`s.

We can write these as follows:
```scala
given (PublishError mitigates WriteError) = PublishError(PublishIssue.Disk).waive
given (PublishError mitigates ShellError) = PublishError(PublishIssue.Shell).waive
given (PublishError mitigates HttpError) = PublishError(PublishIssue.Internet).waive
```

Note that the `waive` method from
[Rudiments](https://github.com/propensive/rudiments/) is used to transform the
value into a lambda whose variable is discarded.

Each given definition provides a new `Mitigation` instance, which will be used
to construct a `Raises[WriteError]`, `Raises[ShellError]` or
`Raises[HttpError]` as necessary, using a contextual `Raises[PublishError]`.
These may be defined locally to the method, or in a more universal scope.

A full example might look like this:
```scala
def publish3(): Unit raises PublishError =
  given (PublishError mitigates WriteError) = PublishError(PublishIssue.Disk).waive
  given (PublishError mitigates ShellError) = PublishError(PublishIssue.Shell).waive
  given (PublishError mitigates HttpError) = PublishError(PublishIssue.Internet).waive
  
  write()
  invoke()
  sendRequest()
```



## Status

Contingency is classified as __embryotic__. For reference, Soundness projects are
categorized into one of the following five stability levels:

- _embryonic_: for experimental or demonstrative purposes only, without any guarantees of longevity
- _fledgling_: of proven utility, seeking contributions, but liable to significant redesigns
- _maturescent_: major design decisions broady settled, seeking probatory adoption and refinement
- _dependable_: production-ready, subject to controlled ongoing maintenance and enhancement; tagged as version `1.0.0` or later
- _adamantine_: proven, reliable and production-ready, with no further breaking changes ever anticipated

Projects at any stability level, even _embryonic_ projects, can still be used,
as long as caution is taken to avoid a mismatch between the project's stability
level and the required stability and maintainability of your own project.

Contingency is designed to be _small_. Its entire source code currently consists
of 543 lines of code.

## Building

Contingency will ultimately be built by Fury, when it is published. In the
meantime, two possibilities are offered, however they are acknowledged to be
fragile, inadequately tested, and unsuitable for anything more than
experimentation. They are provided only for the necessity of providing _some_
answer to the question, "how can I try Contingency?".

1. *Copy the sources into your own project*
   
   Read the `fury` file in the repository root to understand Contingency's build
   structure, dependencies and source location; the file format should be short
   and quite intuitive. Copy the sources into a source directory in your own
   project, then repeat (recursively) for each of the dependencies.

   The sources are compiled against the latest nightly release of Scala 3.
   There should be no problem to compile the project together with all of its
   dependencies in a single compilation.

2. *Build with [Wrath](https://github.com/propensive/wrath/)*

   Wrath is a bootstrapping script for building Contingency and other projects in
   the absence of a fully-featured build tool. It is designed to read the `fury`
   file in the project directory, and produce a collection of JAR files which can
   be added to a classpath, by compiling the project and all of its dependencies,
   including the Scala compiler itself.
   
   Download the latest version of
   [`wrath`](https://github.com/propensive/wrath/releases/latest), make it
   executable, and add it to your path, for example by copying it to
   `/usr/local/bin/`.

   Clone this repository inside an empty directory, so that the build can
   safely make clones of repositories it depends on as _peers_ of `contingency`.
   Run `wrath -F` in the repository root. This will download and compile the
   latest version of Scala, as well as all of Contingency's dependencies.

   If the build was successful, the compiled JAR files can be found in the
   `.wrath/dist` directory.

## Contributing

Contributors to Contingency are welcome and encouraged. New contributors may like
to look for issues marked
[beginner](https://github.com/propensive/contingency/labels/beginner).

We suggest that all contributors read the [Contributing
Guide](/contributing.md) to make the process of contributing to Contingency
easier.

Please __do not__ contact project maintainers privately with questions unless
there is a good reason to keep them private. While it can be tempting to
repsond to such questions, private answers cannot be shared with a wider
audience, and it can result in duplication of effort.

## Author

Contingency was designed and developed by Jon Pretty, and commercial support and
training on all aspects of Scala 3 is available from [Propensive
O&Uuml;](https://propensive.com/).



## Name

_Contingency_ (the library) provides various forms of mitagation and
contingency in the event that an exception occurs at runtime.

In general, Soundness project names are always chosen with some rationale,
however it is usually frivolous. Each name is chosen for more for its
_uniqueness_ and _intrigue_ than its concision or catchiness, and there is no
bias towards names with positive or "nice" meanings—since many of the libraries
perform some quite unpleasant tasks.

Names should be English words, though many are obscure or archaic, and it
should be noted how willingly English adopts foreign words. Names are generally
of Greek or Latin origin, and have often arrived in English via a romance
language.

## Logo

The logo shows three tickets, each of which has been _validated_.

## License

Contingency is copyright &copy; 2024 Jon Pretty & Propensive O&Uuml;, and
is made available under the [Apache 2.0 License](/license.md).

