/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.web.handler.impl;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.MIMEHeader;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.ErrorHandler;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * @author <a href="http://pmlopes@gmail.com">Paulo Lopes</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ErrorHandlerImpl implements ErrorHandler {

  private final Logger log = LoggerFactory.getLogger(ErrorHandlerImpl.class);

  /**
   * Flag to enable/disable printing the full stack trace of exceptions.
   */
  private final boolean displayExceptionDetails;

  /**
   * Cached template for rendering the html errors
   */
  private final String errorTemplate;

  public ErrorHandlerImpl(Vertx vertx, String errorTemplateName, boolean displayExceptionDetails) {
    Objects.requireNonNull(errorTemplateName);
    this.errorTemplate = vertx.fileSystem()
      .readFileBlocking(errorTemplateName)
      .toString(StandardCharsets.UTF_8);
    this.displayExceptionDetails = displayExceptionDetails;
  }

  @Override
  public void handle(RoutingContext context) {

    HttpServerResponse response = context.response();

    Throwable failure = context.failure();

    if (response.headWritten()) {
      // response is already being processed, so we can't really
      // format the error as a "pretty print" message
      log.error("Unexpected error on route", failure);

      try {
        // force a close of the socket to
        // avoid dangling connections
        response.close();
      } catch (RuntimeException e) {
        // ignore
      }
      return;
    }

    int errorCode = context.statusCode();

    // force default error code
    if (errorCode == -1) {
      errorCode = 500;
    }

    response.setStatusCode(errorCode);
    String errorMessage = response.getStatusMessage();

    if (displayExceptionDetails) {
      // failure message may be null
      String exceptionMessage = failure == null ? null : failure.getMessage();
      if (exceptionMessage != null) {
        // no new lines are allowed in the status message
        exceptionMessage = exceptionMessage.replaceAll("[\\r\\n]", " ");
        // apply the newly desired message
        response.setStatusMessage(exceptionMessage);
        //Override the default errorMessage
        errorMessage = exceptionMessage;
      }
    }

    answerWithError(context, errorCode, errorMessage);
  }

  private void answerWithError(RoutingContext context, int errorCode, String errorMessage) {
    if (!sendErrorResponseMIME(context, errorCode, errorMessage) && !sendErrorAcceptMIME(context, errorCode, errorMessage)) {
      // fallback plain/text
      sendError(context, "text/plain", errorCode, errorMessage);
    }
  }

  private boolean sendErrorResponseMIME(RoutingContext context, int errorCode, String errorMessage) {
    // does the response already set the mime type?
    String mime = context.response().headers().get(HttpHeaders.CONTENT_TYPE);

    if (mime == null) {
      // does the route have an acceptable content type?
      mime = context.getAcceptableContentType();
    }

    return mime != null && sendError(context, mime, errorCode, errorMessage);
  }

  private boolean sendErrorAcceptMIME(RoutingContext context, int errorCode, String errorMessage) {
    // respect the client accept order
    List<MIMEHeader> acceptableMimes = context.parsedHeaders().accept();

    for (MIMEHeader accept : acceptableMimes) {
      if (sendError(context, accept.value(), errorCode, errorMessage)) {
        return true;
      }
    }
    return false;
  }

  private boolean sendError(RoutingContext context, String mime, int errorCode, String errorMessage) {

    final String title = "An unexpected error occurred";

    HttpServerResponse response = context.response();

    if (mime.startsWith("text/html")) {
      StringBuilder stack = new StringBuilder();
      if (context.failure() != null && displayExceptionDetails) {
        for (StackTraceElement elem : context.failure().getStackTrace()) {
          stack.append("<li>").append(elem).append("</li>");
        }
      }
      response.putHeader(HttpHeaders.CONTENT_TYPE, "text/html");
      response.end(
        errorTemplate
          .replace("{title}", title)
          .replace("{errorCode}", Integer.toString(errorCode))
          .replace("{errorMessage}", errorMessage)
          .replace("{stackTrace}", stack.toString())
      );
      return true;
    }

    if (mime.startsWith("application/json")) {
      JsonObject jsonError = new JsonObject();
      jsonError.put("error", new JsonObject().put("code", errorCode).put("message", errorMessage));
      if (context.failure() != null && displayExceptionDetails) {
        JsonArray stack = new JsonArray();
        for (StackTraceElement elem : context.failure().getStackTrace()) {
          stack.add(elem.toString());
        }
        jsonError.put("stack", stack);
      }
      response.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
      response.end(jsonError.encode());
      return true;
    }

    if (mime.startsWith("text/plain")) {
      response.putHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
      StringBuilder sb = new StringBuilder();
      sb.append("Error ");
      sb.append(errorCode);
      sb.append(": ");
      sb.append(errorMessage);
      if (context.failure() != null && displayExceptionDetails) {
        for (StackTraceElement elem : context.failure().getStackTrace()) {
          sb.append("\tat ").append(elem).append("\n");
        }
      }
      response.end(sb.toString());
      return true;
    }

    return false;
  }
}
