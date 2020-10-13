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

import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Flash
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}

import scala.concurrent.ExecutionContext.Implicits.global

class DummyJourneyControllerSpec
    extends UnitSpec
    with OneAppPerSuite
    with StateAndBreadcrumbsMatchers {

  implicit val context: DummyContext = DummyContext()

  override lazy val app: Application = new GuiceApplicationBuilder().build()

  lazy val journeyState: DummyJourneyService = app.injector.instanceOf[DummyJourneyService]
  lazy val controller: DummyJourneyController =
    app.injector.instanceOf[DummyJourneyController]

  import journeyState.model.State

  def fakeRequest = FakeRequest()

  "DummyJourneyController" should {
    "after POST /start transition to Start" in {
      journeyState.clear
      val result = controller.start(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "after GET /start transition to Start when uninitialized" in {
      journeyState.clear
      val result = controller.showStart(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "after GET /start show Start when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showStart(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl: after GET /start show Start when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.showStartDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "after GET /start show previous Start when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showStart(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "dsl: after GET /start show previous Start when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showStartDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Start, Nil)
    }

    "after POST /continue transition to Continue when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.continue(fakeRequest.withFormUrlEncodedBody("arg" -> "dummy"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "dsl: after POST /continue transition to Continue when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.continueDsl(fakeRequest.withFormUrlEncodedBody("arg" -> "dummy"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "after invalid POST /continue stay in Start when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.continue(fakeRequest.withFormUrlEncodedBody())
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "dsl: after invalid POST /continue stay in Start when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.continueDsl(fakeRequest.withFormUrlEncodedBody())
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/start")
      journeyState.get           should have[State](State.Start, Nil)
    }

    "after POST /continue transition to Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.continue(fakeRequest.withFormUrlEncodedBody("arg" -> "foo"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get should have[State](
        State.Continue("dummy,foo"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl: after POST /continue transition to Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.continueDsl(fakeRequest.withFormUrlEncodedBody("arg" -> "foo"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      journeyState.get should have[State](
        State.Continue("dummy,foo"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "after invalid POST /continue stay in Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.continue(fakeRequest.withFormUrlEncodedBody("foo" -> "arg"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      flash(result)            shouldBe Flash(Map("foo" -> "arg"))
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "dsl: after invalid POST /continue stay in Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.continueDsl(fakeRequest.withFormUrlEncodedBody("foo" -> "arg"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/continue")
      flash(result)            shouldBe Flash(Map("foo" -> "arg"))
      journeyState.get           should have[State](State.Continue("dummy"), List(State.Start))
    }

    "after POST /continue stay in Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.continue(fakeRequest.withFormUrlEncodedBody("arg" -> "foo"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl: after POST /continue stay in Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.continueDsl(fakeRequest.withFormUrlEncodedBody("arg" -> "foo"))
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "after GET /continue show Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showContinue(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "dsl: after GET /continue show Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showContinueDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "dsl2: after GET /continue show Continue when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.showOrApplyContinueDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "after GET /continue show previous Continue when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.showContinue(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "dsl: after GET /continue show previous Continue when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.showContinueDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Continue("dummy"), List(State.Start))
    }

    "dsl2: after GET /continue show new Continue when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Continue("dummy"), State.Start))
      val result = controller.showOrApplyContinueDsl(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](
        State.Continue("ymmud"),
        List(State.Stop("dummy"), State.Continue("dummy"), State.Start)
      )
    }

    "after GET /continue go to Start when in Stop but no breadcrumbs" in {
      journeyState.set(State.Stop("dummy"), Nil)
      val result = controller.showContinue(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](State.Start, List(State.Stop("dummy")))
    }

    "dsl: after GET /continue go to Start when in Stop but no breadcrumbs" in {
      journeyState.set(State.Stop("dummy"), Nil)
      val result = controller.showContinueDsl(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](State.Start, List(State.Stop("dummy")))
    }

    "dsl2: after GET /continue show new Continue when in Stop but no breadcrumbs" in {
      journeyState.set(State.Stop("dummy"), Nil)
      val result = controller.showOrApplyContinueDsl(fakeRequest)
      status(result) shouldBe 303
      journeyState.get should have[State](State.Continue("ymmud"), List(State.Stop("dummy")))
    }

    "after POST /stop transition to Stop when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.stop(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop(""), List(State.Start))
    }

    "dsl: after POST /stop transition to Stop when in Start" in {
      journeyState.set(State.Start, Nil)
      val result = controller.stopDsl(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop(""), List(State.Start))
    }

    "after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stop(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "dsl: after POST /stop transition to Stop when in Continue" in {
      journeyState.set(State.Continue("dummy"), List(State.Start))
      val result = controller.stopDsl(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get should have[State](
        State.Stop("dummy"),
        List(State.Continue("dummy"), State.Start)
      )
    }

    "after POST /stop stay in Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Start))
      val result = controller.stop(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop("dummy"), List(State.Start))
    }

    "dsl: after POST /stop stay in Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Start))
      val result = controller.stopDsl(fakeRequest)
      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/stop")
      journeyState.get           should have[State](State.Stop("dummy"), List(State.Start))
    }

    "after GET /stop show Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Start))
      val result = controller.showStop(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Stop("dummy"), List(State.Start))
    }

    "dsl: after GET /stop show Stop when in Stop" in {
      journeyState.set(State.Stop("dummy"), List(State.Start))
      val result = controller.showStopDsl(fakeRequest)
      status(result) shouldBe 200
      journeyState.get should have[State](State.Stop("dummy"), List(State.Start))
    }

  }

}
