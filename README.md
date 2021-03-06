# play-fsm
This library provides State Machine building blocks for a stateful Play application.

[ ![Download](https://api.bintray.com/packages/hmrc/releases/play-fsm/images/download.svg) ](https://bintray.com/hmrc/releases/play-fsm/_latestVersion)

## Table of contents

* [About](#about)
    * [Advanced examples](#advanced-examples)
* [How-tos](#how-tos)
* [Patterns](#patterns)
    * [Model](#model-patterns)
    * [Controller](#controller-patterns)
    * [Service](#service-patterns)
    * [Authorization](#authorization-patterns)

## About
    
### Motivation
Managing adequately stateful user journey through the complex business transaction is a challenge. 
It is even more of a challenge in a traditional server-oriented web application built on top of a stateless-by-design HTTP protocol.

Common requirements are:
- the application has to accumulate business transaction input based on multiple prior interactions with the user
- depending on the user selections and decisions various business outcomes are possible (including final lack of action)
- only a few possible journey paths are permitted, the application has to validate each time what user can do given the current state
- the application must be able to acquire, cache and show additional data sourced asynchronously from upstream services
- user must be able to go back and change her input before final transaction will take place
- out-of-order and rogue request handling, introducing malformed state leading to invalid business transaction has to be prevented
- the testability of an application must not be compromised by the intrinsic implementation complexities

### Solution
State Machine is an established pattern to manage complex internal state flow based on a set of transition rules. 
See <https://brilliant.org/wiki/finite-state-machines/>.

In this library, you find a ready-to-use solution tailored for use in an HMRC-style frontend Play application, like `agent-invitations-frontend`. 

### Design
The key concept is a **Journey**.
Each journey represents separate business transaction.
Each journey has a single root state.

Journey is only loosely related to the HTTP and user session, in fact, depending on the state persistence
implementation it can be a part of a user session, span multiple sessions or cross authorisation boundary. 
It is expected of an application to consist of one or more journeys. 

Journey is build out of **State**s and **Transition**s. 

**State** can be anything but usually it will be a set of case classes/objects representing the stage and data of a business transaction. 
State is not expected to have finite values, can be continuous if needed!

**Transition** is a means of moving from one state to another. It's type is a partial async function `State => Future[State]`. 
Transition should be a *pure* function, depending only on its own parameters and the current state. 
External async requests to the upstream services should be provided as a function-type parameters. 

**Breadcrumbs** is a reversed list of previous states (the head is the last one) forming journey history.
History is available only to the *service* and *controller* layers, *model* by design has no implicit knowledge of the history.
Breadcrumbs allow for safe backlinking and rewinding.
If needed *controller* and *service* can exercise fine control over the journey history.

### Benefits
- proper concern separation: 
    - *model* defines core and *pure* business logic decoupled from the application implementation details,
    - *controller* is responsible for wiring user interactions (HTTP requests and responses, HTML forms and pages) into the model transitions,
    - *service* acts as glue between controller and model, taking care of state persistence and breadcrumbs management.
- lightweight, complete and fast testing of a core journey model without spanning a Play application or an HTTP server.

### Features
- `JourneyModel` state and transition model
- `JourneyService` basic state and breadcrumbs services
- `PersistentJourneyService` persistence plug-in
- `JourneyController` base controller trait with common action builders
- `JsonStateFormats` state to json serialization and deserialization builder
- `JourneyIdSupport` mix into JourneyController to feature unique journeyId in the Play session

### Advanced examples:
- Agent Invitations: 
    - Models: <https://github.com/hmrc/agent-invitations-frontend/tree/master/app/uk/gov/hmrc/agentinvitationsfrontend/journeys>
    - Controllers: <https://github.com/hmrc/agent-invitations-frontend/blob/master/app/uk/gov/hmrc/agentinvitationsfrontend/controllers/>
- Agent-Client relationships management help-desk: 
    - Models: <https://github.com/hmrc/agent-client-management-helpdesk-frontend/blob/master/app/uk/gov/hmrc/agentclientmanagementhelpdeskfrontend/journeys/>
    - Controllers: <https://github.com/hmrc/agent-client-management-helpdesk-frontend/blob/master/app/uk/gov/hmrc/agentclientmanagementhelpdeskfrontend/controllers/>

### Best practices
- Keep a single model definition in a single file.
- Name states as nouns and transitions as verbs.
- Carefully balance when to introduce new state and when to add properties to the existing one(s).
- Use a rule of thumb to keep only relevant data in the state.
- Try to avoid optional properties; their presence usually suggest splitting the state.
- Do NOT put functions and framework components in a state; a state should be immutable and serializable.
- Define transitions using curried methods. It works well with action builders.
- When the transition depends on some external operation(s), pass it as a function(s).

## How-tos

### Where to start?

You can use g8 template <https://github.com/hmrc/template-play-26-frontend-fsm.g8> as a start.

### How to add play-fsm library to your existing service?

In your SBT build add:

    resolvers += Resolver.bintrayRepo("hmrc", "releases")
    
    libraryDependencies += "uk.gov.hmrc" %% "play-fsm" % "0.x.0-play-25"
    
or 
    
    libraryDependencies += "uk.gov.hmrc" %% "play-fsm" % "0.x.0-play-26"
    
### How to build a model?
- First, try to visualise user interacting with your application in any possible way. 
- Think about translating pages and forms into a diagram of states and transitions.
- Notice the required inputs and knowledge accumulated at each user journey stage.
- Create a new model object and define there the rules of the game, see an example in <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyModel.scala>.
- Create a unit test to validate all possible states and transitions outcomes, see an example in <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyModelSpec.scala>.

### How to persist in the state?
- play-fsm is not opinionated about state persistence and session management choice but provides an abstract API in the `PersistentJourneyService`.
- `JsonStateFormats` helps to encode/decode state to JSON when using MongoDB or an external REST service, e.g. <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyStateFormats.scala>.

### How to define a controller?
- Create a controller as usual extending `JourneyController` trait.
- Decide 2 things:
    - How to wire action calls into model transitions, use provided action helpers selection, see <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyController.scala>.
    - How to display the state after GET call, implement `renderState`
- Map all GET calls to states, implement `getCallFor` method
- Use `backlinkFor` method to get back link call given breadcrumbs
- GET actions should be idempotent, i.e. should only render existing or historical state.
- POST actions should always invoke some state transition and be followed be a redirect.

### What is RequestContext type parameter?
The type parameter `[RequestContext]` is the type of an implicit context information expected to be
available throughout every action body and in the bottom layers (i.e. persistence, connectors). 
In the HMRC case it is a `HeaderCarrier`.

Inside your `XYZController extends JourneyController[MyContext]` implement:

    override implicit def context(implicit rh: RequestHeader): MyContext = MyContext(...)

## Patterns

### Model patterns

#### State definition patterns

- finite state: sealed trait and a set of case classes/objects

```
    sealed trait State
    
    object State {
        case object Start extends State
        case class Continue(arg: String) extends State
        case class Stop(result: String) extends State
        case object TheEnd
    }
```

- continuous state: class or trait or a primitive value wrapper

```
    type State = String
```

- marking error states

```
    sealed trait State
    sealed trait IsError
    
    object State {
        ...
        case class Failed extends State with IsError
    }
```

#### Transition definition patterns

- simple transition depending only on a current state

```
    val start = Transition {
        case State.Start        => goto(State.Start)
        case State.Continue(_)  => goto(State.Start)
        case State.Stop(_)      => goto(State.Stop)
    }
```

- transition depending on a state and single parameter

```
    def stop(user: User) = Transition {
        case Start              => goto(Stop(""))
        case Continue(value)    => goto(Stop(value))
    }
```

- transition depending on a state and multiple parameters

```
    def continue(user: User)(input: String) = Transition {
        case Start              => goto(Continue(arg))
        case Continue(value)    => goto(Continue(value + "," + input))
    }
```

- transition depending on a state, parameter and an async data source

```
    def continue(user: User)(externalCall: Int => Future[String]) = Transition {
        case Start              => externalCall.map(input => goto(Continue(input)))
        case Continue(value)    => externalCall.map(input => goto(Continue(value + "," + input)))
    }
```

### Controller patterns

- render current or previous state matching expectation

```
    val showStart: Action[AnyContent] = actionShowState {
        case State.Start =>
      }
```

- make a state transition and redirect to the new state

```
    val stop: Action[AnyContent] = action { implicit request =>
        apply(Transitions.stop)(redirect)
      }
```

- clear or refine journey history after transition (cleanBreadcrumbs)

```
    def showTheEndState = action { implicit request =>
        showStateWhenAuthorised(AsUser) {
          case _: State.TheEnd =>
        }.andThen {
          // clears journey history
          case Success(_) => journeyService.cleanBreadcrumbs()
        }
      }
```

- to enforce unique journeyId in the session mixin `JourneyIdSupport`

```
    ... extends Controller
            with JourneyController[MyContext]
            with JourneyIdSupport[MyContext] {
```

- authorisation

### Service patterns

- do not keep error states in the journey history (breadcrumbs)

```
    override val breadcrumbsRetentionStrategy: Breadcrumbs => Breadcrumbs =
        _.filterNot(s => s.isInstanceOf[model.IsError])
```

- keep only previous step

```
    override val breadcrumbsRetentionStrategy: Breadcrumbs => Breadcrumbs =
        _.take(1)
```

### Authorization patterns

- wrap your own authorisation logic returning some `User` entity

```
    val asUser: WithAuthorised[User] = { implicit request => body =>
        myAuthFunction(request) match {
            case None       => Future.failed(...)
            case Some(user) => body(user)
        }
      }
```

- transition only when user has been authorized

```
    val stop: Action[AnyContent] = action { implicit request =>
        whenAuthorised(asUser)(Transitions.stop)(redirect)
      }
```

- display page for an authorized user only

```
    val showContinue: Action[AnyContent] = actionShowStateWhenAuthorised(asUser) {
        case State.Continue(_) =>
      }
```

