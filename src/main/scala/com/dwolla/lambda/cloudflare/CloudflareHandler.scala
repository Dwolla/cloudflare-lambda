package com.dwolla.lambda.cloudflare

import cats.data._
import cats.effect._
import com.dwolla.fs2aws.kms.KmsDecrypter
import com.dwolla.lambda.cloudflare.requests.ResourceRequestFactory
import com.dwolla.lambda.cloudformation._
import fs2.Stream
import org.http4s.Headers
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import org.http4s.client.middleware.Logger
import org.http4s.syntax.string._

import scala.concurrent.ExecutionContext.Implicits.global

class CloudflareHandler(httpClientStream: Stream[IO, Client[IO]], kmsClientStream: Stream[IO, KmsDecrypter[IO]]) extends AbstractCustomResourceHandler[IO] {
  def this() = this(
    Http1Client.stream[IO]().map(Logger(logHeaders = true, logBody = true, (Headers.SensitiveHeaders + "X-Auth-Key".ci).contains)),
    KmsDecrypter.stream[IO](),
  )

  protected lazy val resourceRequestFactory = new ResourceRequestFactory(httpClientStream, kmsClientStream)

  override def handleRequest(req: CloudFormationCustomResourceRequest): IO[HandlerResponse] =
    OptionT(resourceRequestFactory.process(req).compile.last)
      .getOrElseF(IO.raiseError(new RuntimeException("no response was created by the handler")))

}
