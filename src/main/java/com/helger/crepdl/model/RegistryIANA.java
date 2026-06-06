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
 * The IANA charset registry. The {@link #name()} is the charset name passed to
 * {@link java.nio.charset.Charset#forName(String)}.
 *
 * @param name
 *        Charset name (e.g. "ISO-8859-1"), may be <code>null</code> if <code>miBenum</code> is set.
 * @param miBenum
 *        IANA MIB enumeration value. Not currently supported.
 * @author Philip Helger
 */
public record RegistryIANA (@Nullable String name, @Nullable Integer miBenum) implements IRegistry
{
  public RegistryIANA
  {
    if (name == null && miBenum == null)
      throw new IllegalArgumentException ("Both @name and @number are missing for registry IANA");
  }
}
