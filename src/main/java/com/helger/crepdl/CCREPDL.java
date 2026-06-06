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
package com.helger.crepdl;

import com.helger.annotation.concurrent.Immutable;

/**
 * Constants for the CREPDL (ISO/IEC 19757-7) syntax.
 *
 * @author Philip Helger
 */
@Immutable
public final class CCREPDL
{
  /** XML namespace URI of CREPDL version 2.0. */
  public static final String NAMESPACE_URI_V2 = "http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0";

  public static final String ELEMENT_UNION = "union";
  public static final String ELEMENT_INTERSECTION = "intersection";
  public static final String ELEMENT_DIFFERENCE = "difference";
  public static final String ELEMENT_REF = "ref";
  public static final String ELEMENT_REPERTOIRE = "repertoire";
  public static final String ELEMENT_CHAR = "char";
  public static final String ELEMENT_KERNEL = "kernel";
  public static final String ELEMENT_HULL = "hull";

  public static final String ATTR_MODE = "mode";
  public static final String ATTR_MIN_UCS_VERSION = "minUcsVersion";
  public static final String ATTR_MAX_UCS_VERSION = "maxUcsVersion";
  public static final String ATTR_HREF = "href";
  public static final String ATTR_REGISTRY = "registry";
  public static final String ATTR_NAME = "name";
  public static final String ATTR_NUMBER = "number";
  public static final String ATTR_VERSION = "version";

  public static final String MODE_CHARACTER = "character";
  public static final String MODE_GRAPHEME_CLUSTER = "graphemeCluster";

  public static final String REGISTRY_10646 = "10646";
  public static final String REGISTRY_CLDR = "CLDR";
  public static final String REGISTRY_IANA = "IANA";
  public static final String REGISTRY_IVD = "IVD";

  /** Default minimum UCS version (encoded as int per {@link #versionString2Int}). */
  public static final int DEFAULT_MIN_VERSION_INT = versionString2Int ("2.0");
  /** Default maximum UCS version (encoded as int per {@link #versionString2Int}). */
  public static final int DEFAULT_MAX_VERSION_INT = versionString2Int ("17.0");

  private CCREPDL ()
  {}

  /**
   * Encode a dotted version string like "5.2" or "5.2.1" into a single int. The
   * encoding mirrors the F# reference implementation: major * 10000 + minor *
   * 100 + patch.
   *
   * @param sVersion
   *        Version string, never <code>null</code>.
   * @return The encoded integer.
   * @throws IllegalArgumentException
   *         if the version string cannot be parsed.
   */
  public static int versionString2Int (final String sVersion)
  {
    final String [] aParts = sVersion.split ("\\.");
    try
    {
      switch (aParts.length)
      {
        case 1:
          return Integer.parseInt (aParts[0]) * 10000;
        case 2:
          return Integer.parseInt (aParts[0]) * 10000 + Integer.parseInt (aParts[1]) * 100;
        case 3:
          return Integer.parseInt (aParts[0]) * 10000 +
                 Integer.parseInt (aParts[1]) * 100 +
                 Integer.parseInt (aParts[2]);
        default:
          throw new IllegalArgumentException ("incorrect version number: " + sVersion);
      }
    }
    catch (final NumberFormatException ex)
    {
      throw new IllegalArgumentException ("incorrect version number: " + sVersion, ex);
    }
  }
}
