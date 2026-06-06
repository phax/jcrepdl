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

import org.jspecify.annotations.NonNull;

/**
 * The Ideographic Variation Database (IVD) registry. The collection name is required (e.g.
 * "Adobe-Japan1", "Hanyo-Denshi").
 *
 * @param name
 *        The IVD collection name. Never <code>null</code>.
 * @author Philip Helger
 */
public record RegistryIVD (@NonNull String name) implements IRegistry
{
  public RegistryIVD
  {
    if (name == null)
      throw new IllegalArgumentException ("@name is missing for registry IVD");
  }
}
