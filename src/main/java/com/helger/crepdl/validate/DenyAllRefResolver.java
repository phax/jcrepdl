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

import java.net.URI;

import org.jspecify.annotations.NonNull;

import com.helger.crepdl.parse.CREPDLParseException;

/**
 * Refuses every CREPDL <code>&lt;ref&gt;</code> URI. This is the default resolver used by
 * {@link CREPDLValidator} when none is supplied; callers whose scripts contain
 * <code>&lt;ref&gt;</code> elements must explicitly configure a different resolver.
 *
 * @author Philip Helger
 */
public final class DenyAllRefResolver implements ICREPDLRefResolver
{
  public static final DenyAllRefResolver INSTANCE = new DenyAllRefResolver ();

  private DenyAllRefResolver ()
  {}

  @NonNull
  public CREPDLRefSource resolve (@NonNull final URI aHref)
  {
    throw new CREPDLParseException ("CREPDL <ref href='" +
                                    aHref +
                                    "'> rejected: no ref resolver is configured (default is deny-all)");
  }
}
