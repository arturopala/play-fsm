/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.play.fsm

import play.api.data.Form
import play.api.mvc._

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/**
  * Controller mixin for journeys implementing Finite State Machine.
  *
  * Provides 3 main extension points:
  *   - getCallFor: what is the endpoint representing given state
  *   - renderState: how to render given state
  *   - context: custom request context to carry over to the service layer
  *
  * and actions DSL
  *
  * and action creation helpers:
  *   - action
  *   - actionShowState
  *   - actionShowStateWhenAuthorised
  *
  * and async result functions:
  *   - showState
  *   - showStateWhenAuthorised
  *   - whenAuthorised
  *   - whenAuthorisedWithForm
  *   - whenAuthorisedWithBootstrapAndForm
  *   - bindForm
  */
trait JourneyController[RequestContext] {

  /** This has to be injected in the concrete controller */
  val journeyService: JourneyService[RequestContext]

  import journeyService.{Breadcrumbs, StateAndBreadcrumbs}
  import journeyService.model.{State, Transition, TransitionNotAllowed}

  //-------------------------------------------------
  // EXTENSION POINTS
  //-------------------------------------------------

  /** implement to map states into endpoints for redirection and back linking */
  def getCallFor(state: State)(implicit request: Request[_]): Call

  /** implement to render state after transition or when form validation fails */
  def renderState(state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]])(implicit
    request: Request[_]
  ): Result

  /** implement to provide customized request context you require in the service layer * */
  def context(implicit rh: RequestHeader): RequestContext

  /** interceptor: override to do basic checks on every incoming request (headers, session, etc.) */
  def withValidRequest(
    body: => Future[Result]
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    body

  type Route        = Request[_] => Result
  type RouteFactory = StateAndBreadcrumbs => Route

  /** displays template for the state and breadcrumbs, eventually override to change details */
  val display: RouteFactory = (state: StateAndBreadcrumbs) =>
    (request: Request[_]) => renderState(state._1, state._2, None)(request)

  /** redirects to the endpoint matching state, eventually override to change details */
  val redirect: RouteFactory =
    (state: StateAndBreadcrumbs) =>
      (request: Request[_]) => Results.Redirect(getCallFor(state._1)(request))

  //-------------------------------------------------
  // HELPERS
  //-------------------------------------------------

  /** applies transition to the current state */
  protected final def apply(transition: Transition, routeFactory: RouteFactory)(implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] =
    journeyService
      .apply(transition)
      .map(routeFactory)
      .map(_(request))
      .recover {
        case TransitionNotAllowed(origin, breadcrumbs, _) =>
          routeFactory((origin, breadcrumbs))(request) // renders current state back
      }

  /** gets call to the previous state */
  protected def backLinkFor(breadcrumbs: Breadcrumbs)(implicit request: Request[_]): Call =
    breadcrumbs.headOption.map(getCallFor).getOrElse(getCallFor(journeyService.model.root))

  /** action for a given async result function */
  protected final def action(
    body: Request[_] => Future[Result]
  )(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val rc: RequestContext = context(request)
      withValidRequest(body(request))
    }

  type WithAuthorised[User] = Request[_] => (User => Future[Result]) => Future[Result]

  /** applies transition parametrized by an authorised user */
  protected final def whenAuthorised[User](
    withAuthorised: WithAuthorised[User]
  )(transition: User => Transition)(
    routeFactory: RouteFactory
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { user: User =>
      apply(transition(user), routeFactory)
    }

  /** applies transition parametrized by an authorised user and form output */
  protected final def whenAuthorisedWithForm[User, Payload](
    withAuthorised: WithAuthorised[User]
  )(form: Form[Payload])(
    transition: User => Payload => Transition
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { user: User =>
      bindForm(form, transition(user))
    }

  /** applies 2 transitions in order:
    * - first transition as provided
    * - second transition parametrized by an authorised user and form output
    */
  protected final def whenAuthorisedWithBootstrapAndForm[User, Payload, T](
    bootstrap: Transition
  )(withAuthorised: WithAuthorised[User])(form: Form[Payload])(
    transition: User => Payload => Transition
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { user: User =>
      journeyService
        .apply(bootstrap)
        .flatMap(_ => bindForm(form, transition(user)))
    }

  /** binds form and applies transition parametrized by its outcome if success,
    * otherwise redirects to the current state with form errors in flash scope
    */
  protected final def bindForm[T](form: Form[T], transition: T => Transition)(implicit
    rc: RequestContext,
    request: Request[_],
    ec: ExecutionContext
  ): Future[Result] =
    form
      .bindFromRequest()
      .fold(
        formWithErrors =>
          journeyService.currentState.flatMap {
            case Some((state, _)) =>
              Future.successful(
                Results
                  .Redirect(getCallFor(state))
                  .flashing(Flash {
                    val data = formWithErrors.data
                    // dummy parameter required if empty data
                    if (data.isEmpty) Map("dummy" -> "") else data
                  })
              )
            case None =>
              apply(journeyService.model.start, redirect)
          },
        userInput => apply(transition(userInput), redirect)
      )

  type ExpectedStates = PartialFunction[State, Unit]

  /** action rendering current state or rewinding to the previous state matching expectation */
  protected final def actionShowState(
    expectedStates: ExpectedStates
  )(implicit ec: ExecutionContext): Action[AnyContent] =
    action { implicit request =>
      implicit val rc: RequestContext = context(request)
      showState(expectedStates)
    }

  /** renders current state or rewinds to the previous state matching expectation */
  protected final def showState(
    expectedStates: ExpectedStates
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    for {
      stateAndBreadcrumbsOpt <- journeyService.currentState
      result <- stateAndBreadcrumbsOpt match {
                  case None => apply(journeyService.model.start, redirect)
                  case Some(stateAndBreadcrumbs) =>
                    if (hasState(expectedStates, stateAndBreadcrumbs))
                      journeyService.currentState
                        .flatMap(rewindTo(expectedStates))
                    else apply(journeyService.model.start, redirect)
                }
    } yield result

  /** renders current state or applies transition */
  protected final def showStateOrApply(
    expectedStates: ExpectedStates
  )(
    transition: Transition
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    for {
      stateAndBreadcrumbsOpt <- journeyService.currentState
      result <- stateAndBreadcrumbsOpt match {
                  case None => apply(journeyService.model.start, redirect)
                  case Some((state, breadcrumbs)) =>
                    if (expectedStates.isDefinedAt(state))
                      Future.successful(renderState(state, breadcrumbs, None)(request))
                    else apply(transition, redirect)
                }
    } yield result

  /** action rendering current state or rewinding to the previous state matching expectation */
  protected final def actionShowStateWhenAuthorised[User](
    withAuthorised: WithAuthorised[User]
  )(expectedStates: ExpectedStates)(implicit ec: ExecutionContext): Action[AnyContent] =
    action { implicit request =>
      implicit val rc: RequestContext = context(request)
      showStateWhenAuthorised(withAuthorised)(expectedStates)
    }

  /** when user authorized then render current state or rewinds to the previous state matching expectation */
  protected final def showStateWhenAuthorised[User](withAuthorised: WithAuthorised[User])(
    expectedStates: ExpectedStates
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    withAuthorised(request) { _ =>
      showState(expectedStates)
    }

  /** checks if some expected state can be found in journey history (breadcrumbs) */
  @tailrec
  protected final def hasState(
    filter: PartialFunction[State, Unit],
    stateAndBreadcrumbs: StateAndBreadcrumbs
  ): Boolean =
    stateAndBreadcrumbs match {
      case (state, breadcrumbs) =>
        if (filter.isDefinedAt(state)) true
        else
          breadcrumbs match {
            case Nil    => false
            case s :: b => hasState(filter, (s, b))
          }
    }

  /** rewinds journey state and history (breadcrumbs) back to the latest expected state */
  protected final def rewindTo(expectedState: PartialFunction[State, Unit])(
    stateAndBreadcrumbsOpt: Option[StateAndBreadcrumbs]
  )(implicit rc: RequestContext, request: Request[_], ec: ExecutionContext): Future[Result] =
    stateAndBreadcrumbsOpt match {
      case None => apply(journeyService.model.start, redirect)
      case Some((state, breadcrumbs)) =>
        if (expectedState.isDefinedAt(state))
          Future.successful(renderState(state, breadcrumbs, None)(request))
        else journeyService.stepBack.flatMap(rewindTo(expectedState))
    }

  // ---------------------------------------
  //  DSL
  // ---------------------------------------

  /** Collection of DSL-style action builders */
  object actions {

    /**
      * Display the state requested by the type parameter S.
      * If the current state is not of type S,
      * then rewind history back to the nearest state matching S,
      * or to the root state.
      * @tparam S type of the state to display
      */
    def show[S <: State: ClassTag](implicit ec: ExecutionContext): Action[AnyContent] =
      action { implicit request =>
        implicit val rc: RequestContext = context(request)
        JourneyController.this.showState { case _: S => }
      }

    /** Apply state transition and redirect to the URL matching the new state. */
    def apply(
      transition: Transition
    )(implicit ec: ExecutionContext): Action[AnyContent] =
      action { implicit request =>
        implicit val rc: RequestContext = context(request)
        JourneyController.this.apply(transition, JourneyController.this.redirect)
      }

    /**
      * Display the journey state requested by the type parameter S.
      * If the current state is not of type S,
      * then apply the transition and redirect to the new state.
      * If transition is not allowed then redirect back to the current state.
      * @tparam S type of the state to display
      */
    def showOrApply[S <: State: ClassTag](
      transition: Transition
    )(implicit ec: ExecutionContext): Action[AnyContent] =
      action { implicit request =>
        implicit val rc: RequestContext = context(request)
        JourneyController.this.showStateOrApply { case _: S => }(transition)
      }

    /**
      * Bind the form to the request.
      * If valid, apply the following transition,
      * if not valid, redirect back to the current state with failed form.
      * @tparam Payload form output type
      */
    def bindForm[Payload](form: Form[Payload]): BindForm[Payload] =
      new BindForm[Payload](form)

    class BindForm[Payload] private[fsm] (form: Form[Payload]) {

      /**
        * Apply state transition parametrized by the form output
        * and redirect to the URL matching the new state.
        */
      def apply(
        transition: Payload => Transition
      )(implicit ec: ExecutionContext): Action[AnyContent] =
        action { implicit request =>
          implicit val rc: RequestContext = context(request)
          JourneyController.this.bindForm(form, transition)
        }
    }

    /**
      * Progress only if authorization succeeds.
      * Use returned value in the following operations.
      * @tparam User authorised user information type
      */
    def whenAuthorised[User](withAuthorised: WithAuthorised[User]): WhenAuthorised[User] =
      new WhenAuthorised[User](withAuthorised)

    class WhenAuthorised[User] private[fsm] (withAuthorised: WithAuthorised[User]) {

      /**
        * Display the state requested by the type parameter S.
        * If the current state is not of type S,
        * then tries to rewind history back to the nearest state matching S,
        * or to the root state.
        * @tparam S type of the state to display
        */
      def show[S <: State: ClassTag](implicit ec: ExecutionContext): Action[AnyContent] =
        action { implicit request =>
          implicit val rc: RequestContext = context(request)
          JourneyController.this.showStateWhenAuthorised(withAuthorised) { case _: S => }
        }

      /**
        * Apply state transition parametrized by the user information
        * and redirect to the URL matching the new state.
        */
      def apply(
        transition: User => Transition
      )(implicit ec: ExecutionContext): Action[AnyContent] =
        action { implicit request =>
          withAuthorised(request) { user: User =>
            implicit val rc: RequestContext = context(request)
            JourneyController.this.apply(transition(user), JourneyController.this.redirect)
          }
        }

      /**
        * Display the state requested by the type parameter S.
        * If the current state is not of type S,
        * then apply the transition and redirect to the new state.
        * If transition is not allowed then redirect back to the current state.
        * @tparam S type of the state to display
        */
      def showOrApply[S <: State: ClassTag](
        transition: User => Transition
      )(implicit ec: ExecutionContext): Action[AnyContent] =
        action { implicit request =>
          implicit val rc: RequestContext = context(request)
          withAuthorised(request) { user: User =>
            JourneyController.this.showStateOrApply { case _: S => }(transition(user))
          }
        }

      /**
        * Bind the form to the request.
        * If valid, apply the following transitions,
        * if not valid, redirect back to the current state with failed form.
        * @tparam Payload form output type
        */
      def bindForm[Payload](form: Form[Payload]): BindForm[Payload] =
        new BindForm[Payload](form)

      class BindForm[Payload] private[fsm] (form: Form[Payload]) {

        /**
          * Apply state transition parametrized by the user information and form output
          * and redirect to the URL matching the end state.
          */
        def apply(
          transition: User => Payload => Transition
        )(implicit ec: ExecutionContext): Action[AnyContent] =
          action { implicit request =>
            withAuthorised(request) { user: User =>
              implicit val rc: RequestContext = context(request)
              JourneyController.this.bindForm(form, transition(user))
            }
          }
      }
    }
  }
}
