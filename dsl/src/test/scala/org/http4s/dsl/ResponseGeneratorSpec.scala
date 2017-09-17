package org.http4s
package dsl

import org.http4s.headers.{Accept, `Content-Length`, `Content-Type`}
import org.http4s.internal.compatibility._

class ResponseGeneratorSpec extends Http4sSpec {

  "Add the EntityEncoder headers along with a content-length header" in {
    val body = "foo"
    val resultheaders = Ok(body)(EntityEncoder.stringEncoder).unsafePerformSync.headers
    EntityEncoder.stringEncoder.headers.foldLeft(ok) { (old, h) =>
      old and (resultheaders.exists(_ == h) must_=== true)
    }

    resultheaders.get(`Content-Length`) must_=== `Content-Length`.fromLong(body.getBytes.length.toLong).toOption
  }

  "Not duplicate headers when not provided" in {
    val w = EntityEncoder.encodeBy[String](EntityEncoder.stringEncoder.headers.put(Accept(MediaRange.`audio/*`)))(
                            EntityEncoder.stringEncoder.toEntity(_))

    Ok("foo")(w).unsafePerformSync.headers.get(Accept).get.values.list must_=== List(MediaRange.`audio/*`)
  }

  "Explicitly added headers have priority" in {
    val w = EntityEncoder.encodeBy[String](EntityEncoder.stringEncoder.headers.put(`Content-Type`(MediaType.`text/html`)))(
      EntityEncoder.stringEncoder.toEntity(_)
    )

    Ok("foo", Headers(`Content-Type`(MediaType.`application/json`)))(w)
      .unsafePerformSync.headers.get(`Content-Type`) must_=== Some(`Content-Type`(MediaType.`application/json`))
  }

  "NoContent() does not generate Content-Length" in {
    /* A server MUST NOT send a Content-Length header field in any response
     * with a status code of 1xx (Informational) or 204 (No Content).
     * -- https://tools.ietf.org/html/rfc7230#section-3.3.2
     */
    val resp = NoContent()
    resp.map(_.contentLength) must returnValue(None)
  }

  "ResetContent() generates Content-Length: 0" in {
    /* a server MUST do one of the following for a 205 response: a) indicate a
     * zero-length body for the response by including a Content-Length header
     * field with a value of 0; b) indicate a zero-length payload for the
     * response by including a Transfer-Encoding header field with a value of
     * chunked and a message body consisting of a single chunk of zero-length;
     * or, c) close the connection immediately after sending the blank line
     * terminating the header section.
     * -- https://tools.ietf.org/html/rfc7231#section-6.3.6
     *
     * We choose option a.
     */
    val resp = ResetContent()
    resp.map(_.contentLength) must returnValue(Some(0))
  }

  "NotModified() does not generate Content-Length" in {
    /* A server MAY send a Content-Length header field in a 304 (Not Modified)
     * response to a conditional GET request (Section 4.1 of [RFC7232]); a
     * server MUST NOT send Content-Length in such a response unless its
     * field-value equals the decimal number of octets that would have been sent
     * in the payload body of a 200 (OK) response to the same request.
     * -- https://tools.ietf.org/html/rfc7230#section-3.3.2
     *
     * We don't know what the proper value is in this signature, so we send
     * nothing.
     */
    val resp = NotModified()
    resp.map(_.contentLength) must returnValue(None)
  }

  "EntityResponseGenerator() generates Content-Length: 0" in {
    /**
     * Aside from the cases defined above, in the absence of Transfer-Encoding,
     * an origin server SHOULD send a Content-Length header field when the
     * payload body size is known prior to sending the complete header section.
     * -- https://tools.ietf.org/html/rfc7230#section-3.3.2
     *
     * Until someone sets a body, we have an empty body and we'll set the
     * Content-Length.
     */
    val resp = Ok()
    resp.map(_.contentLength) must returnValue(Some(0))
  }
}