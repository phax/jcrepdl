/*
 * Copyright (C) 2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.crepdl.validate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Result of {@link ICREPDLRefResolver#resolve(URI)}. Carries the byte stream of
 * the referenced CREPDL script and an optional base URI used for resolving any
 * nested <code>&lt;ref&gt;</code> elements inside the returned document.
 * Implements {@link AutoCloseable} so callers can use it in a
 * try-with-resources block.
 *
 * @author Philip Helger
 */
public record CREPDLRefSource (@NonNull InputStream stream, @Nullable URI baseUri) implements AutoCloseable
{
  @Override
  public void close () throws IOException
  {
    stream.close ();
  }
}
