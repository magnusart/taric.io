package io.taric.services

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{GivenWhenThen, FeatureSpec, BeforeAndAfterAll}
import org.scalatest.matchers.ShouldMatchers
import io.taric.TestSetup._

/**
 * File created: 2013-01-20 22:18
 * 
 * Copyright Solvies AB 2013
 * For licensing information see LICENSE file
 */
class ImportFlowActorSpec ( _system:ActorSystem ) extends TestKit( _system ) with ImplicitSender with FeatureSpec
  with GivenWhenThen with ShouldMatchers with BeforeAndAfterAll {
  def this( ) = this( ActorSystem( "TestSystem3", testConf) )

  override def afterAll {
    system.shutdown( )
  }

  info("As subscriber to Taric updates")
  info("I want to have changes to the Taric list imported and peristed automatically")
  info("So that I do not have to manually check and import for updates")

  scenario("First import, import and persist all Taric product codes (happy flow).") {
    Given("The system is in an idle state")
    And("new taric data is available")
    And("a persistent datastore is available")
    When("the start import event is published")
    Then("the import should gather system configuration")
    Then("browse for new remote resources")
    Then("determine which of these resources to fetch")
    Then("fetch the remote resources, yielding line records")
    Then("parse the line records into structured TaricCodes")
    Then("merge information about existing codes, new codes and replaced codes")
    Then("persist codes into the persistent data store")
    pending
  }



}
