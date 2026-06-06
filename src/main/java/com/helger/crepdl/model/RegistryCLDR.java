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
package com.helger.crepdl.model;

import org.jspecify.annotations.Nullable;

/**
 * The CLDR registry. Not implemented by this library (matches upstream F#).
 *
 * @param version
 *        CLDR version, may be <code>null</code>.
 * @param name
 *        CLDR collection name, may be <code>null</code>.
 * @author Philip Helger
 */
public record RegistryCLDR (@Nullable String version, @Nullable String name) implements IRegistry
{
  /* nothing else */
}
