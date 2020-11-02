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

import io.vertx.core.http.HttpMethod;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.webauthn.WebAuthn;
import io.vertx.ext.auth.webauthn.WebAuthnCredentials;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.WebAuthnHandler;
import io.vertx.ext.web.impl.Origin;

public class WebAuthnHandlerImpl implements WebAuthnHandler {

  private static final Logger LOG = LoggerFactory.getLogger(WebAuthnHandlerImpl.class);

  private final WebAuthn webAuthn;
  // the extra routes
  private Route register = null;
  private Route login = null;
  private Route response = null;
  // optional config
  private String origin;
  private String domain;

  public WebAuthnHandlerImpl(WebAuthn webAuthN) {
    this.webAuthn = webAuthN;
  }

  private static boolean containsRequiredString(JsonObject json, String key) {
    try {
      if (json == null) {
        return false;
      }
      if (!json.containsKey(key)) {
        return false;
      }
      Object s = json.getValue(key);
      return (s instanceof String) && !"".equals(s);
    } catch (ClassCastException e) {
      return false;
    }
  }

  private static boolean containsOptionalString(JsonObject json, String key) {
    try {
      if (json == null) {
        return true;
      }
      if (!json.containsKey(key)) {
        return true;
      }
      Object s = json.getValue(key);
      return (s instanceof String);
    } catch (ClassCastException e) {
      return false;
    }
  }

  private static boolean containsRequiredObject(JsonObject json, String key) {
    try {
      if (json == null) {
        return false;
      }
      if (!json.containsKey(key)) {
        return false;
      }
      JsonObject s = json.getJsonObject(key);
      return s != null;
    } catch (ClassCastException e) {
      return false;
    }
  }

  private static boolean matchesRoute(RoutingContext ctx, Route route) {
    if (route != null) {
      return ctx.request().method() == HttpMethod.POST && ctx.normalizedPath().equals(route.getPath());
    }
    return false;
  }

  @Override
  public void handle(RoutingContext ctx) {

    if (response == null) {
      LOG.error("No callback mounted!");
      ctx.fail(500);
      return;
    }

    if (matchesRoute(ctx, response)) {
      if (LOG.isWarnEnabled()) {
        LOG.warn("The callback route is shaded by the WebAuthNAuthHandler, ensure the callback route is added BEFORE the WebAuthNAuthHandler route!");
      }
      ctx.fail(500);
      return;
    }

    if (matchesRoute(ctx, register)) {
      if (LOG.isWarnEnabled()) {
        LOG.warn("The register callback route is shaded by the WebAuthNAuthHandler, ensure the callback route is added BEFORE the WebAuthNAuthHandler route!");
      }
      ctx.fail(500);
      return;
    }

    if (matchesRoute(ctx, login)) {
      if (LOG.isWarnEnabled()) {
        LOG.warn("The login callback route is shaded by the WebAuthNAuthHandler, ensure the callback route is added BEFORE the WebAuthNAuthHandler route!");
      }
      ctx.fail(500);
      return;
    }

    if (ctx.user() == null) {
      ctx.fail(401);
    } else {
      ctx.next();
    }
  }


  @Override
  public WebAuthnHandler setupCredentialsCreateCallback(Route route) {
    this.register = route
      // force a post if otherwise
      .method(HttpMethod.POST)
      .handler(ctx -> {
        try {
          // might throw runtime exception if there's no json or is bad formed
          final JsonObject webauthnRegister = ctx.getBodyAsJson();
          final Session session = ctx.session();

          // the register object should match a Webauthn user.
          // A user has only a required field: name
          // And optional fields: displayName and icon
          if (!containsRequiredString(webauthnRegister, "name")) {
            LOG.warn("missing 'name' field from request json");
            ctx.fail(400);
          } else {
            // input basic validation is OK

            if (session == null) {
              LOG.warn("No session or session handler is missing.");
              ctx.fail(500);
              return;
            }

            webAuthn.createCredentialsOptions(webauthnRegister, createCredentialsOptions -> {
              if (createCredentialsOptions.failed()) {
                ctx.fail(createCredentialsOptions.cause());
                return;
              }

              final JsonObject credentialsOptions = createCredentialsOptions.result();

              // save challenge to the session
              ctx.session()
                .put("challenge", credentialsOptions.getString("challenge"))
                .put("username", webauthnRegister.getString("name"));

//              ctx.json(credentialsOptions.put("status", "ok").put("errorMessage", ""));
              ctx.json(credentialsOptions);
            });
          }
        } catch (IllegalArgumentException e) {
          LOG.error("Illegal request", e);
          ctx.fail(400);
        } catch (RuntimeException e) {
          LOG.error("Unexpected exception", e);
          ctx.fail(e);
        }
      });
    return this;
  }

  @Override
  public WebAuthnHandler setupCredentialsGetCallback(Route route) {
    this.login = route
      // force a post if otherwise
      .method(HttpMethod.POST)
      .handler(ctx -> {
        try {
          // might throw runtime exception if there's no json or is bad formed
          final JsonObject webauthnLogin = ctx.getBodyAsJson();
          final Session session = ctx.session();

          if (!containsRequiredString(webauthnLogin, "name")) {
            LOG.debug("Request missing 'name' field");
            ctx.fail(400);
            return;
          }

          // input basic validation is OK

          if (session == null) {
            LOG.warn("No session or session handler is missing.");
            ctx.fail(500);
            return;
          }

          final String username = webauthnLogin.getString("name");

          // STEP 18 Generate assertion
          webAuthn.getCredentialsOptions(username, generateServerGetAssertion -> {
            if (generateServerGetAssertion.failed()) {
              LOG.error("Unexpected exception", generateServerGetAssertion.cause());
              ctx.fail(generateServerGetAssertion.cause());
              return;
            }

            final JsonObject getAssertion = generateServerGetAssertion.result();

            session
              .put("challenge", getAssertion.getString("challenge"))
              .put("username", username);

//            ctx.json(getAssertion.put("status", "ok").put("errorMessage", ""));
            ctx.json(getAssertion);
          });
        } catch (IllegalArgumentException e) {
          LOG.error("Illegal request", e);
          ctx.fail(400);
        } catch (RuntimeException e) {
          LOG.error("Unexpected exception", e);
          ctx.fail(e);
        }
      });
    return this;
  }

  @Override
  public WebAuthnHandler setupCallback(Route route) {
    this.response = route
      // force a post if otherwise
      .method(HttpMethod.POST)
      .handler(ctx -> {
        try {
          // might throw runtime exception if there's no json or is bad formed
          final JsonObject webauthnResp = ctx.getBodyAsJson();
          // input validation
          if (
            !containsRequiredString(webauthnResp, "id") ||
              !containsRequiredString(webauthnResp, "rawId") ||
              !containsRequiredObject(webauthnResp, "response") ||
              !containsOptionalString(webauthnResp.getJsonObject("response"), "userHandle") ||
              !containsRequiredString(webauthnResp, "type") ||
              !"public-key".equals(webauthnResp.getString("type"))) {

            LOG.debug("Response missing one or more of id/rawId/response[.userHandle]/type fields, or type is not public-key");
            ctx.fail(400);
            return;
          }

          // input basic validation is OK

          final Session session = ctx.session();

          if (ctx.session() == null) {
            LOG.error("No session or session handler is missing.");
            ctx.fail(500);
            return;
          }

          webAuthn.authenticate(
            // authInfo
            new WebAuthnCredentials()
              .setOrigin(origin)
              .setDomain(domain)
              .setChallenge(session.get("challenge"))
              .setUsername(session.get("username"))
              .setWebauthn(webauthnResp), authenticate -> {

              // invalidate the challenge
              session.remove("challenge");

              if (authenticate.succeeded()) {
                final User user = authenticate.result();
                // save the user into the context
                ctx.setUser(user);
                // the user has upgraded from unauthenticated to authenticated
                // session should be upgraded as recommended by owasp
                session.regenerateId();
//                ctx.json(new JsonObject().put("status", "ok").put("errorMessage", ""));
                ctx.response().end();
              } else {
                LOG.error("Unexpected exception", authenticate.cause());
                ctx.fail(authenticate.cause());
              }
            });
        } catch (IllegalArgumentException e) {
          LOG.error("Illegal request", e);
          ctx.fail(400);
        } catch (RuntimeException e) {
          LOG.error("Unexpected exception", e);
          ctx.fail(e);
        }
      });

    return this;
  }

  @Override
  public WebAuthnHandler setOrigin(String origin) {
    if (origin != null) {
      Origin o = Origin.parse(origin);
      this.origin = o.encode();
      domain = o.host();
    } else {
      this.origin = null;
      domain = null;
    }
    return this;
  }
}
