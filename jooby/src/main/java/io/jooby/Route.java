/**
 * Jooby https://jooby.io
 * Apache License Version 2.0 https://jooby.io/LICENSE.txt
 * Copyright 2014 Edgar Espina
 */
package io.jooby;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Route contains information about the HTTP method, path pattern, which content types consumes and
 * produces, etc..
 *
 * Additionally, contains metadata about route return Java type, argument source (query, path, etc..) and
 * Java type.
 *
 * This class contains all the metadata associated to a route. It is like a {@link Class} object
 * for routes.
 *
 * @author edgar
 * @since 2.0.0
 */
public class Route {

  /**
   * Decorates a route handler by running logic before and after route handler. This pattern is
   * also known as Filter.
   *
   * <pre>{@code
   * {
   *   decorator(next -> ctx -> {
   *     long start = System.currentTimeMillis();
   *     Object result = next.apply(ctx);
   *     long end = System.currentTimeMillis();
   *     System.out.println("Took: " + (end - start));
   *     return result;
   *   });
   * }
   * }</pre>
   *
   * @author edgar
   * @since 2.0.0
   */
  public interface Decorator {
    /**
     * Chain the decorator within next handler.
     *
     * @param next Next handler.
     * @return A new handler.
     */
    @Nonnull Handler apply(@Nonnull Handler next);

    /**
     * Chain this decorator with another and produces a new decorator.
     *
     * @param next Next decorator.
     * @return A new decorator.
     */
    @Nonnull default Decorator then(@Nonnull Decorator next) {
      return h -> apply(next.apply(h));
    }

    /**
     * Chain this decorator with a handler and produces a new handler.
     *
     * @param next Next handler.
     * @return A new handler.
     */
    @Nonnull default Handler then(@Nonnull Handler next) {
      return ctx -> apply(next).apply(ctx);
    }
  }

  /**
   * Decorates a handler and run logic before handler is executed.
   *
   * @author edgar
   * @since 2.0.0
   */
  public interface Before extends Decorator {
    @Nonnull @Override default Handler apply(@Nonnull Handler next) {
      return ctx -> {
        before(ctx);
        return next.apply(ctx);
      };
    }

    /**
     * Execute application code before next handler.
     *
     * @param ctx Web context.
     * @throws Exception If something goes wrong.
     */
    void before(@Nonnull Context ctx) throws Exception;
  }

  /**
   * Execute application logic after a response has been generated by a route handler.
   *
   * @author edgar
   * @since 2.0.0
   */
  public interface After {

    /**
     * Chain this decorator with next one and produces a new after decorator.
     *
     * @param next Next decorator.
     * @return A new decorator.
     */
    @Nonnull default After then(@Nonnull After next) {
      return (ctx, result) -> apply(ctx, next.apply(ctx, result));
    }

    /**
     * Execute application logic on a route response.
     *
     * @param ctx Web context.
     * @param result Response generated by route handler.
     * @return Response to send.
     * @throws Exception If something goes wrong.
     */
    @Nonnull Object apply(@Nonnull Context ctx, Object result) throws Exception;
  }

  /**
   * Route handler here is where the application logic lives.
   *
   * @author edgar
   * @since 2.0.0
   */
  public interface Handler extends Serializable {

    /**
     * Allows a handler to listen for route metadata.
     *
     * @param route Route metadata.
     * @return This handler.
     */
    default @Nonnull Handler setRoute(@Nonnull Route route) {
      // NOOP
      return this;
    }

    /**
     * Execute application code.
     *
     * @param ctx Web context.
     * @return Route response.
     * @throws Exception If something goes wrong.
     */
    @Nonnull Object apply(@Nonnull Context ctx) throws Exception;

    /**
     * Chain this after decorator with next and produces a new decorator.
     *
     * @param next Next decorator.
     * @return A new handler.
     */
    @Nonnull default Handler then(@Nonnull After next) {
      return ctx -> next.apply(ctx, apply(ctx));
    }
  }

  /**
   * Handler for {@link StatusCode#NOT_FOUND} responses.
   */
  public static final Handler NOT_FOUND = ctx -> ctx.sendError(new StatusCodeException(StatusCode.NOT_FOUND));

  /**
   * Handler for {@link StatusCode#METHOD_NOT_ALLOWED} responses.
   */
  public static final Handler METHOD_NOT_ALLOWED = ctx -> ctx
      .sendError(new StatusCodeException(StatusCode.METHOD_NOT_ALLOWED));

  /**
   * Favicon handler as a silent 404 error.
   */
  public static final Handler FAVICON = ctx -> ctx.send(StatusCode.NOT_FOUND);

  private static final List<MediaType> EMPTY_LIST = Collections.emptyList();

  private final Map<String, Parser> parsers;

  private String pattern;

  private String method;

  private List<String> pathKeys;

  private Decorator before;

  private Handler handler;

  private After after;

  private Handler pipeline;

  private Renderer renderer;

  private Type returnType;

  private Object handle;

  private List<MediaType> produces = EMPTY_LIST;

  private List<MediaType> consumes = EMPTY_LIST;

  /**
   * Creates a new route.
   *
   * @param method HTTP method.
   * @param pattern Path pattern.
   * @param pathKeys Path keys.
   * @param returnType Return type.
   * @param handler Route handler.
   * @param before Before pipeline.
   * @param after After pipeline.
   * @param renderer Route renderer.
   * @param parsers Route parsers.
   */
  public Route(@Nonnull String method,
      @Nonnull String pattern,
      @Nonnull List<String> pathKeys,
      @Nonnull Type returnType,
      @Nonnull Handler handler,
      @Nullable Decorator before,
      @Nullable After after,
      @Nonnull Renderer renderer,
      @Nonnull Map<String, Parser> parsers) {
    this.method = method.toUpperCase();
    this.pattern = pattern;
    this.returnType = returnType;
    this.handler = handler;
    this.before = before;
    this.after = after;
    this.renderer = renderer;
    this.pathKeys = pathKeys;
    this.parsers = parsers;
    this.handle = handler;

    this.pipeline = handler;
    if (before != null) {
      this.pipeline = before.then(pipeline);
    }
    if (after != null) {
      this.pipeline = this.pipeline.then(after);
    }
  }

  /**
   * Creates a new route.
   *
   * @param method HTTP method.
   * @param pattern Path pattern.
   * @param returnType Return type.
   * @param handler Route handler.
   * @param before Before pipeline.
   * @param after After pipeline.
   * @param renderer Route renderer.
   * @param parsers Route parsers.
   */
  public Route(@Nonnull String method,
      @Nonnull String pattern,
      @Nonnull Type returnType,
      @Nonnull Handler handler,
      @Nullable Decorator before,
      @Nullable After after,
      @Nonnull Renderer renderer,
      @Nonnull Map<String, Parser> parsers) {
    this(method, pattern, Router.pathKeys(pattern), returnType, handler, before, after, renderer,
        parsers);
  }

  /**
   * Path pattern.
   *
   * @return Path pattern.
   */
  public @Nonnull String getPattern() {
    return pattern;
  }

  /**
   * HTTP method.
   *
   * @return HTTP method.
   */
  public @Nonnull String getMethod() {
    return method;
  }

  /**
   * Path keys.
   *
   * @return Path keys.
   */
  public @Nonnull List<String> getPathKeys() {
    return pathKeys;
  }

  /**
   * Route handler.
   *
   * @return Route handler.
   */
  public @Nonnull Handler getHandler() {
    return handler;
  }

  /**
   * Route pipeline.
   *
   * @return Route pipeline.
   */
  public @Nonnull Handler getPipeline() {
    return pipeline;
  }

  /**
   * Handler instance which might or might not be the same as {@link #getHandler()}.
   *
   * The handle is required to extract correct metadata.
   *
   * @return Handle.
   */
  public @Nonnull Object getHandle() {
    return handle;
  }

  /**
   * Before pipeline or <code>null</code>.
   *
   * @return Before pipeline or <code>null</code>.
   */
  public @Nullable Decorator getBefore() {
    return before;
  }

  /**
   * After decorator or <code>null</code>.
   *
   * @return After decorator or <code>null</code>.
   */
  public @Nullable After getAfter() {
    return after;
  }

  /**
   * Set route handle instance, required when handle is different from {@link #getHandler()}.
   *
   * @param handle Handle instance.
   * @return This route.
   */
  public Route setHandle(@Nonnull Object handle) {
    this.handle = handle;
    return this;
  }

  /**
   * Set route pipeline. This method is part of public API but isn't intended to be used by public.
   *
   * @param pipeline Pipeline.
   * @return This routes.
   */
  public @Nonnull Route setPipeline(Route.Handler pipeline) {
    this.pipeline = pipeline;
    return this;
  }

  /**
   * Route renderer.
   *
   * @return Route renderer.
   */
  public @Nonnull Renderer getRenderer() {
    return renderer;
  }

  /**
   * Return return type.
   *
   * @return Return type.
   */
  public @Nonnull Type getReturnType() {
    return returnType;
  }

  /**
   * Set route return type.
   *
   * @param returnType Return type.
   * @return This route.
   */
  public @Nonnull Route setReturnType(@Nonnull Type returnType) {
    this.returnType = returnType;
    return this;
  }

  /**
   * Response types (format) produces by this route. If set, we expect to find a match in the
   * <code>Accept</code> header. If none matches, we send a {@link StatusCode#NOT_ACCEPTABLE}
   * response.
   *
   * @return Immutable list of produce types.
   */
  public @Nonnull List<MediaType> getProduces() {
    return produces;
  }

  /**
   * Add one or more response types (format) produces by this route.
   *
   * @param produces Produce types.
   * @return This route.
   */
  public @Nonnull Route produces(@Nonnull MediaType... produces) {
    return setProduces(Arrays.asList(produces));
  }

  /**
   * Add one or more response types (format) produces by this route.
   *
   * @param produces Produce types.
   * @return This route.
   */
  public @Nonnull Route setProduces(@Nonnull Collection<MediaType> produces) {
    if (this.produces == EMPTY_LIST) {
      this.produces = new ArrayList<>();
    }
    produces.forEach(this.produces::add);
    return this;
  }

  /**
   * Request types (format) consumed by this route. If set the <code>Content-Type</code> header
   * is checked against these values. If none matches we send a
   * {@link StatusCode#UNSUPPORTED_MEDIA_TYPE} exception.
   *
   * @return Immutable list of consumed types.
   */
  public @Nonnull List<MediaType> getConsumes() {
    return consumes;
  }

  /**
   * Add one or more request types (format) consumed by this route.
   *
   * @param consumes Consume types.
   * @return This route.
   */
  public @Nonnull Route consumes(@Nonnull MediaType... consumes) {
    return setConsumes(Arrays.asList(consumes));
  }

  /**
   * Add one or more request types (format) consumed by this route.
   *
   * @param consumes Consume types.
   * @return This route.
   */
  public @Nonnull Route setConsumes(@Nonnull Collection<MediaType> consumes) {
    if (this.consumes == EMPTY_LIST) {
      this.consumes = new ArrayList<>();
    }
    consumes.forEach(this.consumes::add);
    return this;
  }

  /**
   * Parser for given media type.
   *
   * @param contentType Media type.
   * @return Parser.
   */
  public @Nonnull Parser parser(@Nonnull MediaType contentType) {
    return parsers.getOrDefault(contentType.getValue(), Parser.UNSUPPORTED_MEDIA_TYPE);
  }

  @Override public String toString() {
    return method + " " + pattern;
  }
}
