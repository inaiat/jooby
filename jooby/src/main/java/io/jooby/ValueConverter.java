/**
 * Jooby https://jooby.io
 * Apache License Version 2.0 https://jooby.io/LICENSE.txt
 * Copyright 2014 Edgar Espina
 */
package io.jooby;

import javax.annotation.Nonnull;

/**
 * Value converter for simple values that come from query, path, form, etc... parameters into more
 * specific type.
 *
 * It is an extension point for {@link Value#to(Class)} calls.
 */
public interface ValueConverter {
  /**
   * True if the converter applies for the given type.
   *
   * @param type Conversion type.
   * @return True if the converter applies for the given type.
   */
  boolean supports(@Nonnull Class type);

  /**
   * Convert simple to specific type.
   *
   * @param value Value value.
   * @param type Requested type.
   * @return Converted value.
   */
  Object convert(@Nonnull Value value, @Nonnull Class type);
}
