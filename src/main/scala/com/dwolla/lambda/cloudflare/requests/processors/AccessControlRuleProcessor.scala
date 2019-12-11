package com.dwolla.lambda.cloudflare.requests.processors

import cats.effect._
import com.dwolla.circe._
import com.dwolla.cloudflare._
import com.dwolla.cloudflare.domain.model.ZoneId
import com.dwolla.cloudflare.domain.model.accesscontrolrules._
import com.dwolla.lambda.cloudflare.Exceptions._
import com.dwolla.lambda.cloudformation.CloudFormationRequestType._
import com.dwolla.lambda.cloudformation._
import fs2.Stream
import io.circe._
import io.circe.syntax._

class AccessControlRuleProcessor[F[_] : Sync](zoneClient: ZoneClient[F], accessControlRuleClient: AccessControlRuleClient[F]) extends ResourceRequestProcessor[F] {

  def this(executor: StreamingCloudflareApiExecutor[F]) = this(ZoneClient(executor), AccessControlRuleClient(executor))

  override def process(action: CloudFormationRequestType,
                       physicalResourceId: Option[PhysicalResourceId],
                       properties: JsonObject): Stream[F, HandlerResponse] =
    for {
      request <- parseRecordFrom[AccessControlRule](properties, "AccessControlRule")
      resp <- handleAction(action, request, physicalResourceId, properties)
    } yield resp

  private def handleAction(action: CloudFormationRequestType,
                           request: AccessControlRule,
                           physicalResourceId: Option[PhysicalResourceId],
                           properties: JsonObject,
                          ): Stream[F, HandlerResponse] = (action, physicalResourceId) match {
    case (CreateRequest, None) => handleCreate(request, properties)
    case (UpdateRequest, PhysicalResourceId(Level.Zone(zid), prid)) => handleUpdate(zid, prid, request)
    case (DeleteRequest, PhysicalResourceId(Level.Zone(zid), prid)) => handleDelete(zid, prid)
    case (CreateRequest, Some(id)) => Stream.raiseError(UnexpectedPhysicalId(id))
    case (_, Some(id)) => Stream.raiseError(InvalidCloudflareUri(id))
    case (UpdateRequest, None) | (DeleteRequest, None) => Stream.raiseError(MissingPhysicalId(action))
    case (OtherRequestType(_), _) => Stream.raiseError(UnsupportedRequestType(action))
  }

  private def handleCreate(request: AccessControlRule, properties: JsonObject): Stream[F, HandlerResponse] =
    for {
      zone <- parseRecordFrom[String](properties, "Zone")
      zoneId <- zoneClient.getZoneId(zone)
      resp <- accessControlRuleClient.create(Level.Zone(zoneId), request)
    } yield HandlerResponse(tagPhysicalResourceId(resp.id.fold("Unknown AccessControlRule ID")(id => accessControlRuleClient.buildUri(Level.Zone(zoneId), id))), JsonObject(
      "created" -> resp.asJson
    ))

  private def handleUpdate(zoneId: ZoneId, ruleId: AccessControlRuleId, request: AccessControlRule): Stream[F, HandlerResponse] =
    for {
      resp <- accessControlRuleClient.update(Level.Zone(zoneId), request.copy(id = Option(ruleId)))
    } yield HandlerResponse(tagPhysicalResourceId(accessControlRuleClient.buildUri(Level.Zone(zoneId), resp.id.getOrElse(ruleId))), JsonObject(
      "updated" -> resp.asJson
    ))

  private def handleDelete(zoneId: ZoneId, ruleId: AccessControlRuleId): Stream[F, HandlerResponse] =
    for {
      resp <- accessControlRuleClient.delete(Level.Zone(zoneId), ruleId)
    } yield HandlerResponse(tagPhysicalResourceId(accessControlRuleClient.buildUri(Level.Zone(zoneId), resp)), JsonObject(
      "deleted" -> resp.asJson
    ))

  private object PhysicalResourceId {
    def unapply(arg: Option[PhysicalResourceId]): Option[(Level, AccessControlRuleId)] =
      arg.flatMap(accessControlRuleClient.parseUri)
  }
}
