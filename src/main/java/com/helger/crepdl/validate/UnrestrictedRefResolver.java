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
import java.net.URI;
import java.net.URL;

import org.jspecify.annotations.NonNull;

import com.helger.crepdl.parse.CREPDLParseException;

/**
 * Dereferences any URI through {@link URI#toURL()} and {@link URL#openStream()}.
 * Preserves the pre-SPI behaviour of the expander: any scheme the JVM's URL
 * handlers understand (file, http, https, jar, ...) is fetched with no
 * allow-list, host filter, timeout, or size cap.
 *
 * <p>
 * <strong>This resolver is unsafe for untrusted CREPDL documents.</strong> A
 * hostile script can read local files, probe internal HTTP services, or hang
 * the parsing thread on a slow URL. Use only when every CREPDL document fed to
 * the validator originates from a trusted source.
 * </p>
 *
 * @author Philip Helger
 */
public final class UnrestrictedRefResolver implements ICREPDLRefResolver
{
  public static final UnrestrictedRefResolver INSTANCE = new UnrestrictedRefResolver ();

  private UnrestrictedRefResolver ()
  {}

  @NonNull
  public CREPDLRefSource resolve (@NonNull final URI aHref)
  {
    try
    {
      final URL aURL = aHref.toURL ();
      return new CREPDLRefSource (aURL.openStream (), aHref);
    }
    catch (final IOException | IllegalArgumentException ex)
    {
      throw new CREPDLParseException ("Failed to dereference CREPDL <ref href='" + aHref + "'>", ex);
    }
  }
}
