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
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;

import com.helger.crepdl.parse.CREPDLParseException;

/**
 * Allows CREPDL <code>&lt;ref&gt;</code> URIs with the <code>file:</code>
 * scheme whose resolved, normalised path lies under a single caller-supplied
 * root directory. URIs with any other scheme, or with a resolved path that
 * escapes the root (via <code>..</code> segments or absolute references), are
 * refused.
 *
 * <p>
 * Note: this resolver does not de-reference symbolic links before the
 * containment check. If the root directory contains a symlink that points
 * outside, a ref through that symlink will be accepted.
 * </p>
 *
 * @author Philip Helger
 */
public final class FileSystemRefResolver implements ICREPDLRefResolver
{
  private static final String SCHEME_FILE = "file";

  private final Path m_aRoot;

  /**
   * @param aRoot
   *        Directory under which referenced files must live. Immediately turned
   *        absolute and normalised. Never <code>null</code>.
   */
  public FileSystemRefResolver (@NonNull final Path aRoot)
  {
    m_aRoot = aRoot.toAbsolutePath ().normalize ();
  }

  @NonNull
  public CREPDLRefSource resolve (@NonNull final URI aHref)
  {
    final String sScheme = aHref.getScheme ();
    if (sScheme == null || !SCHEME_FILE.equalsIgnoreCase (sScheme))
      throw new CREPDLParseException ("CREPDL <ref href='" +
                                      aHref +
                                      "'> rejected: scheme must be '" +
                                      SCHEME_FILE +
                                      "' but was '" +
                                      sScheme +
                                      "'");

    final Path aPath;
    try
    {
      aPath = Path.of (aHref).toAbsolutePath ().normalize ();
    }
    catch (final IllegalArgumentException | FileSystemNotFoundException ex)
    {
      throw new CREPDLParseException ("CREPDL <ref href='" + aHref + "'> rejected: not a valid file path", ex);
    }

    if (!aPath.startsWith (m_aRoot))
      throw new CREPDLParseException ("CREPDL <ref href='" +
                                      aHref +
                                      "'> rejected: resolved path '" +
                                      aPath +
                                      "' is outside the allowed root '" +
                                      m_aRoot +
                                      "'");

    try
    {
      return new CREPDLRefSource (Files.newInputStream (aPath), aHref);
    }
    catch (final IOException ex)
    {
      throw new CREPDLParseException ("Failed to read CREPDL <ref href='" + aHref + "'>", ex);
    }
  }
}
