package com.dwolla.lambda.cloudflare.requests.processors

import java.time.Instant

import cats.effect._
import com.dwolla.cloudflare.domain.model._
import com.dwolla.cloudflare.domain.model.accesscontrolrules._
import com.dwolla.cloudflare.{AccessControlRuleClient, Level, ZoneClient}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import fs2._
import _root_.io.circe.syntax._
import _root_.io.circe._
import com.dwolla.lambda.cloudflare.Exceptions.InvalidCloudflareUri
import com.dwolla.lambda.cloudformation._
import org.specs2.matcher.IOMatchers
import com.dwolla.circe._
import com.dwolla.cloudflare.domain.model.Exceptions.AccessDenied
import com.dwolla.lambda.cloudflare.JsonObjectMatchers
import com.dwolla.lambda.cloudformation.CloudFormationRequestType._

//noinspection Specs2Matchers
class AccessControlRuleProcessorSpec extends Specification with IOMatchers with JsonObjectMatchers {

  trait Setup extends Scope {
    val zoneId = "zone-id".asInstanceOf[ZoneId]
    val ruleId = "access-control-rule-id".asInstanceOf[AccessControlRuleId]

    def buildProcessor(fakeAccessControlRuleClient: AccessControlRuleClient[IO] = new FakeAccessControlRuleClient,
                       fakeZoneClient: ZoneClient[IO] = new FakeZoneClient,
                      ): AccessControlRuleProcessor[IO] =
      new AccessControlRuleProcessor[IO](fakeZoneClient, fakeAccessControlRuleClient)(null)
  }

  "processing Creates" should {
    "handle a Create request successfully" in new Setup {
      private val fakeAccessControlRuleClient = new FakeAccessControlRuleClient {
        override def create(level: Level, rule: AccessControlRule): Stream[IO, AccessControlRule] =
          Stream.emit(rule.copy(id = Option(ruleId)))
      }
      private val fakeZoneClient = new FakeZoneClient {
        override def getZoneId(domain: String): Stream[IO, ZoneId] =
          domain match {
            case "zone" => Stream.emit("zone-id").map(shapeless.tag[ZoneIdTag][String])
            case _ => Stream.raiseError[IO](AccessDenied())
          }
      }
      private val processor = buildProcessor(fakeAccessControlRuleClient, fakeZoneClient)
      private val accessControlRule = AccessControlRule(
        id = None,
        notes = None,
        mode = shapeless.tag[AccessControlRuleModeTag][String]("challenge"),
        configuration = AccessControlRuleConfiguration(
          target = shapeless.tag[AccessControlRuleConfigurationTargetTag][String]("ip"),
          value = shapeless.tag[AccessControlRuleConfigurationValueTag][String]("1.2.3.4")
        ))

      private val output = processor.process(CreateRequest, None, JsonObject(
        "AccessControlRule" -> accessControlRule.asJson,
        "Zone" -> "zone".asJson,
      ))

      output.compile.last must returnValue(beSome[HandlerResponse].like {
        case handlerResponse =>
          handlerResponse.physicalId must_== fakeAccessControlRuleClient.buildUri(Level.Zone(zoneId), ruleId)
          handlerResponse.data must haveKeyValuePair("created" -> accessControlRule.copy(id = Option(ruleId)).asJson)
      })
    }

    "gracefully handle the case where the AccessControlRule returned by Cloudflare doesn't have an ID" in new Setup {
      private val fakeAccessControlRuleClient = new FakeAccessControlRuleClient {
        override def create(level: Level, rule: AccessControlRule): Stream[IO, AccessControlRule] =
          Stream.emit(accessControlRule.copy(id = None))
      }
      private val fakeZoneClient = new FakeZoneClient {
        override def getZoneId(domain: String): Stream[IO, ZoneId] =
          domain match {
            case "zone" => Stream.emit("zone-id").map(shapeless.tag[ZoneIdTag][String])
            case _ => Stream.raiseError[IO](AccessDenied())
          }
      }
      private val processor = buildProcessor(fakeAccessControlRuleClient, fakeZoneClient)
      private val accessControlRule = AccessControlRule(
        id = None,
        notes = None,
        mode = shapeless.tag[AccessControlRuleModeTag][String]("challenge"),
        configuration = AccessControlRuleConfiguration(
          target = shapeless.tag[AccessControlRuleConfigurationTargetTag][String]("ip"),
          value = shapeless.tag[AccessControlRuleConfigurationValueTag][String]("1.2.3.4")
        ))

      private val output = processor.process(CreateRequest, None, JsonObject(
        "AccessControlRule" -> accessControlRule.asJson,
        "Zone" -> "zone".asJson,
      ))

      output.compile.last must returnValue(beSome[HandlerResponse].like {
        case handlerResponse =>
          handlerResponse.physicalId must_== "Unknown AccessControlRule ID"
          handlerResponse.data must haveKeyValuePair("created" -> accessControlRule.copy(id = None).asJson)
      })
    }

    "fail to create if a physical resource ID has already been specified" in new Setup {
      private val processor = buildProcessor()
      private val accessControlRule = AccessControlRule(
        id = None,
        notes = None,
        mode = shapeless.tag[AccessControlRuleModeTag][String]("challenge"),
        configuration = AccessControlRuleConfiguration(
          target = shapeless.tag[AccessControlRuleConfigurationTargetTag][String]("ip"),
          value = shapeless.tag[AccessControlRuleConfigurationValueTag][String]("1.2.3.4")
        ))

      private val output = processor.process(CreateRequest, Option("physical-resource-id").map(tagPhysicalResourceId), JsonObject(
        "AccessControlRule" -> accessControlRule.asJson,
        "ZoneId" -> "zone-id".asJson,
      ))

      output.compile.toList.attempt must returnValue(equalTo(Left(UnexpectedPhysicalId("physical-resource-id"))))
    }
  }

  "processing Updates" should {
    "handle a normal update request successfully" in new Setup {
      private val fakeAccessControlRuleClient = new FakeAccessControlRuleClient {
        override def update(level: Level, rule: AccessControlRule): Stream[IO, AccessControlRule] =
          Stream.emit(rule.copy(modified_on = Option("2019-01-24T11:09:11.000000Z").map(Instant.parse)))
      }
      private val processor = buildProcessor(fakeAccessControlRuleClient)
      private val accessControlRule = AccessControlRule(
        id = Option(ruleId),
        notes = None,
        mode = shapeless.tag[AccessControlRuleModeTag][String]("challenge"),
        configuration = AccessControlRuleConfiguration(
          target = shapeless.tag[AccessControlRuleConfigurationTargetTag][String]("ip"),
          value = shapeless.tag[AccessControlRuleConfigurationValueTag][String]("1.2.3.4")
        ))

      private val output = processor.process(UpdateRequest, Option(fakeAccessControlRuleClient.buildUri(Level.Zone(zoneId), ruleId)).map(tagPhysicalResourceId), JsonObject(
        "AccessControlRule" -> accessControlRule.asJson,
      ))

      output.compile.last must returnValue(beSome[HandlerResponse].like {
        case handlerResponse =>
          handlerResponse.physicalId must_== fakeAccessControlRuleClient.buildUri(Level.Zone(zoneId), ruleId)
          handlerResponse.data must haveKeyValuePair("updated" -> accessControlRule.copy(modified_on = Option("2019-01-24T11:09:11.000000Z").map(Instant.parse)).asJson)
      })
    }

    "return the existing access control rule ID if Cloudflare fails to return an ID for some reason" in new Setup {
      private val fakeAccessControlRuleClient = new FakeAccessControlRuleClient {
        override def update(level: Level, rule: AccessControlRule): Stream[IO, AccessControlRule] =
          Stream.emit(rule.copy(id = None, modified_on = Option("2019-01-24T11:09:11.000000Z").map(Instant.parse)))
      }
      private val processor = buildProcessor(fakeAccessControlRuleClient)
      private val accessControlRule = AccessControlRule(
        id = Option(ruleId),
        notes = None,
        mode = shapeless.tag[AccessControlRuleModeTag][String]("challenge"),
        configuration = AccessControlRuleConfiguration(
          target = shapeless.tag[AccessControlRuleConfigurationTargetTag][String]("ip"),
          value = shapeless.tag[AccessControlRuleConfigurationValueTag][String]("1.2.3.4")
        ))

      private val output = processor.process(UpdateRequest, Option(fakeAccessControlRuleClient.buildUri(Level.Zone(zoneId), ruleId)).map(tagPhysicalResourceId), JsonObject(
        "AccessControlRule" -> accessControlRule.asJson,
      ))

      output.compile.last must returnValue(beSome[HandlerResponse].like {
        case handlerResponse =>
          handlerResponse.physicalId must_== fakeAccessControlRuleClient.buildUri(Level.Zone(zoneId), ruleId)
          handlerResponse.data must haveKeyValuePair("updated" -> accessControlRule.copy(id = None, modified_on = Option("2019-01-24T11:09:11.000000Z").map(Instant.parse)).asJson)
      })
    }

    "raise an error when the physical resource id cannot be parsed" in new Setup {
      private val fakeAccessControlRuleClient = new FakeAccessControlRuleClient {
        override def parseUri(uri: String): Option[(Level, AccessControlRuleId)] = None
      }
      private val processor = buildProcessor(fakeAccessControlRuleClient)
      private val accessControlRule = AccessControlRule(
        id = Option(ruleId),
        notes = None,
        mode = shapeless.tag[AccessControlRuleModeTag][String]("challenge"),
        configuration = AccessControlRuleConfiguration(
          target = shapeless.tag[AccessControlRuleConfigurationTargetTag][String]("ip"),
          value = shapeless.tag[AccessControlRuleConfigurationValueTag][String]("1.2.3.4")
        ))

      private val output = processor.process(UpdateRequest, Option("unparseable-value").map(tagPhysicalResourceId), JsonObject(
        "AccessControlRule" -> accessControlRule.asJson,
      ))

      output.compile.toList.attempt must returnValue(equalTo(Left(InvalidCloudflareUri("unparseable-value"))))
    }

    "raise an error when the physical resource id is missing" in new Setup {
      private val processor = buildProcessor()
      private val accessControlRule = AccessControlRule(
        id = Option(ruleId),
        notes = None,
        mode = shapeless.tag[AccessControlRuleModeTag][String]("challenge"),
        configuration = AccessControlRuleConfiguration(
          target = shapeless.tag[AccessControlRuleConfigurationTargetTag][String]("ip"),
          value = shapeless.tag[AccessControlRuleConfigurationValueTag][String]("1.2.3.4")
        ))

      private val output = processor.process(UpdateRequest, None, JsonObject(
        "AccessControlRule" -> accessControlRule.asJson,
      ))

      output.compile.toList.attempt must returnValue(equalTo(Left(MissingPhysicalId(UpdateRequest))))
    }
  }

  "processing Deletes" should {
    "return the deleted ID" in new Setup {
      private val fakeAccessControlRuleClient = new FakeAccessControlRuleClient {
        override def delete(level: Level, ruleId: String): Stream[IO, AccessControlRuleId] =
          Stream.emit(ruleId).map(shapeless.tag[AccessControlRuleIdTag][String])
      }
      private val processor = buildProcessor(fakeAccessControlRuleClient)
      private val accessControlRule = AccessControlRule(
        id = Option(ruleId),
        notes = None,
        mode = shapeless.tag[AccessControlRuleModeTag][String]("challenge"),
        configuration = AccessControlRuleConfiguration(
          target = shapeless.tag[AccessControlRuleConfigurationTargetTag][String]("ip"),
          value = shapeless.tag[AccessControlRuleConfigurationValueTag][String]("1.2.3.4")
        ))

      private val output = processor.process(DeleteRequest, Option(fakeAccessControlRuleClient.buildUri(Level.Zone(zoneId), ruleId)).map(tagPhysicalResourceId), JsonObject(
        "AccessControlRule" -> accessControlRule.asJson,
      ))

      output.compile.last must returnValue(beSome[HandlerResponse].like {
        case handlerResponse =>
          handlerResponse.physicalId must_== fakeAccessControlRuleClient.buildUri(Level.Zone(zoneId), ruleId)
          handlerResponse.data must haveKeyValuePair("deleted" -> ruleId.asJson)
      })
    }

    "raise an error when the physical resource id cannot be parsed" in new Setup {
      private val fakeAccessControlRuleClient = new FakeAccessControlRuleClient {
        override def parseUri(uri: String): Option[(Level, AccessControlRuleId)] = None
      }
      private val processor = buildProcessor(fakeAccessControlRuleClient)
      private val accessControlRule = AccessControlRule(
        id = Option(ruleId),
        notes = None,
        mode = shapeless.tag[AccessControlRuleModeTag][String]("challenge"),
        configuration = AccessControlRuleConfiguration(
          target = shapeless.tag[AccessControlRuleConfigurationTargetTag][String]("ip"),
          value = shapeless.tag[AccessControlRuleConfigurationValueTag][String]("1.2.3.4")
        ))

      private val output = processor.process(DeleteRequest, Option("unparseable-value").map(tagPhysicalResourceId), JsonObject(
        "AccessControlRule" -> accessControlRule.asJson,
      ))

      output.compile.toList.attempt must returnValue(equalTo(Left(InvalidCloudflareUri("unparseable-value"))))
    }

    "raise an error when the physical resource id is missing" in new Setup {
      private val processor = buildProcessor()
      private val accessControlRule = AccessControlRule(
        id = Option(ruleId),
        notes = None,
        mode = shapeless.tag[AccessControlRuleModeTag][String]("challenge"),
        configuration = AccessControlRuleConfiguration(
          target = shapeless.tag[AccessControlRuleConfigurationTargetTag][String]("ip"),
          value = shapeless.tag[AccessControlRuleConfigurationValueTag][String]("1.2.3.4")
        ))

      private val output = processor.process(DeleteRequest, None, JsonObject(
        "AccessControlRule" -> accessControlRule.asJson,
      ))

      output.compile.toList.attempt must returnValue(equalTo(Left(MissingPhysicalId(DeleteRequest))))
    }
  }

  "processing other types of requests" should {
    "fail" in new Setup {
      private val processor = buildProcessor()
      private val accessControlRule = AccessControlRule(
        id = Option(ruleId),
        notes = None,
        mode = shapeless.tag[AccessControlRuleModeTag][String]("challenge"),
        configuration = AccessControlRuleConfiguration(
          target = shapeless.tag[AccessControlRuleConfigurationTargetTag][String]("ip"),
          value = shapeless.tag[AccessControlRuleConfigurationValueTag][String]("1.2.3.4")
        ))

      private val output = processor.process(OtherRequestType("other-request-type"), None, JsonObject(
        "AccessControlRule" -> accessControlRule.asJson,
      ))

      output.compile.toList.attempt must returnValue(equalTo(Left(UnsupportedRequestType(OtherRequestType("other-request-type")))))
    }
  }

}

class FakeAccessControlRuleClient extends AccessControlRuleClient[IO] {
  override def list(level: Level, mode: Option[String]): Stream[IO, AccessControlRule] = Stream.raiseError[IO](new NotImplementedError())
  override def getById(level: Level, ruleId: String): Stream[IO, AccessControlRule] = Stream.raiseError[IO](new NotImplementedError())
  override def create(level: Level, rule: AccessControlRule): Stream[IO, AccessControlRule] = Stream.raiseError[IO](new NotImplementedError())
  override def update(level: Level, rule: AccessControlRule): Stream[IO, AccessControlRule] = Stream.raiseError[IO](new NotImplementedError())
  override def delete(level: Level, ruleId: String): Stream[IO, AccessControlRuleId] = Stream.raiseError[IO](new NotImplementedError())
}
