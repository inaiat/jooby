/**
 * Jooby https://jooby.io
 * Apache License Version 2.0 https://jooby.io/LICENSE.txt
 * Copyright 2014 Edgar Espina
 */
package io.jooby.internal;

import io.jooby.Context;
import io.jooby.Cookie;
import io.jooby.Session;
import io.jooby.SessionStore;
import io.jooby.SessionToken;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class SignedSessionStore implements SessionStore {

  private final Function<String, Map<String, String>> decoder;

  private final Function<Map<String, String>, String> encoder;

  private final SessionToken token;

  public SignedSessionStore(SessionToken token, Function<String, Map<String, String>> decoder,
      Function<Map<String, String>, String> encoder) {
    this.decoder = decoder;
    this.encoder = encoder;
    this.token = token;
  }

  @Nonnull @Override public Session newSession(@Nonnull Context ctx) {
    return Session.create(ctx, null).setNew(true);
  }

  @Nullable @Override public Session findSession(@Nonnull Context ctx) {
    String signed = token.findToken(ctx);
    if (signed == null) {
      return null;
    }
    Map<String, String> attributes = decoder.apply(signed);
    if (attributes == null || attributes.size() == 0) {
      return null;
    }
    return Session.create(ctx, signed, new HashMap<>(attributes)).setNew(false);
  }

  @Override public void deleteSession(@Nonnull Context ctx, @Nonnull Session session) {
    token.deleteToken(ctx, null);
  }

  @Override public void touchSession(@Nonnull Context ctx, @Nonnull Session session) {
    token.saveToken(ctx, encoder.apply(session.toMap()));
  }

  @Override public void saveSession(@Nonnull Context ctx, @Nonnull Session session) {
    // NOOP
  }

  @Override public void renewSessionId(@Nonnull Context ctx, @Nonnull Session session) {
    token.saveToken(ctx, encoder.apply(session.toMap()));
  }
}
