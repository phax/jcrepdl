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
 * The ISO/IEC 10646 registry of predefined collections. At least one of {@link #name()} or
 * {@link #number()} must be non-<code>null</code>.
 *
 * @param name
 *        Collection name as per ISO/IEC 10646. May be <code>null</code> if <code>number</code> is
 *        set.
 * @param number
 *        Collection number as per ISO/IEC 10646. May be <code>null</code> if <code>name</code> is
 *        set.
 * @author Philip Helger
 */
public record RegistryISO10646 (@Nullable String name, @Nullable Integer number) implements IRegistry
{
  public RegistryISO10646
  {
    if (name == null && number == null)
      throw new IllegalArgumentException ("Both @name and @number are missing for registry 10646");
  }
}
