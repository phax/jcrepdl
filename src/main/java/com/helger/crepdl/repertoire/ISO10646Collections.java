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
package com.helger.crepdl.repertoire;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.io.nonblocking.NonBlockingBufferedReader;

/**
 * Predefined ISO/IEC 10646 collections shipped with this library. The data is mechanically
 * extracted from the F# reference implementation's <code>ISO10646CollectionsDefinitions.fs</code>.
 * <p>
 * Three indexes are maintained:
 * </p>
 * <ul>
 * <li>{@link #lookupInlineByName} / {@link #lookupInlineByNumber} returns the raw range
 * specification for collections that fit on one line.</li>
 * <li>{@link #lookupOutOfLineResource} returns the resource path to a bundled decimal-range file
 * (for huge collections like IICORE or Age*).</li>
 * <li>{@link #lookupCREPDLScriptResource} returns the resource path to a bundled CREPDL XML script
 * for collections defined recursively via union/intersection/difference.</li>
 * </ul>
 *
 * @author Philip Helger
 */
public final class ISO10646Collections
{
  // Per-registry repo layout under /external/crepdl/repo/<registry>/.
  // Registry "10646" holds three index files + a flat list of bundled
  // hex-range .txt files + a crepdl/ subfolder of CREPDL XML fragments.
  private static final String BASE = "/external/crepdl/repo/10646/";
  private static final String CREPDL_BASE = BASE + "crepdl/";

  private static final Map <Integer, String> INLINE_BY_NUMBER = new HashMap <> ();
  private static final Map <String, String> INLINE_BY_NAME = new HashMap <> ();

  private static final Map <Integer, String> OUTOFLINE_BY_NUMBER = new HashMap <> ();
  private static final Map <String, String> OUTOFLINE_BY_NAME = new HashMap <> ();

  private static final Map <Integer, String> CREPDL_BY_NUMBER = new HashMap <> ();
  private static final Map <String, String> CREPDL_BY_NAME = new HashMap <> ();

  static
  {
    _loadInline ();
    _loadIndex (BASE + "outofline.txt", OUTOFLINE_BY_NUMBER, OUTOFLINE_BY_NAME);
    _loadIndex (BASE + "crepdl-index.txt", CREPDL_BY_NUMBER, CREPDL_BY_NAME);
  }

  private ISO10646Collections ()
  {}

  private static void _loadInline ()
  {
    try (final InputStream aIS = ISO10646Collections.class.getResourceAsStream (BASE + "inline.txt"))
    {
      if (aIS == null)
        throw new IllegalStateException ("Missing resource '" + BASE + "inline.txt'");

      try (final NonBlockingBufferedReader aBR = new NonBlockingBufferedReader (new InputStreamReader (aIS,
                                                                                                       StandardCharsets.UTF_8)))
      {
        String sLine;
        while ((sLine = aBR.readLine ()) != null)
        {
          if (sLine.isEmpty () || sLine.startsWith ("#"))
            continue;
          final String [] aParts = sLine.split ("\\|", 3);
          if (aParts.length != 3)
            continue;
          final Integer nNumber = Integer.valueOf (aParts[0]);
          final String sName = aParts[1];
          final String sSpec = aParts[2];
          INLINE_BY_NUMBER.put (nNumber, sSpec);
          if (!sName.isEmpty ())
            INLINE_BY_NAME.put (sName, sSpec);
        }
      }
    }
    catch (final IOException ex)
    {
      throw new IllegalStateException ("Failed to load inline ISO 10646 collections", ex);
    }
  }

  private static void _loadIndex (@NonNull final String sPath,
                                  @NonNull final Map <Integer, String> aByNumber,
                                  @NonNull final Map <String, String> aByName)
  {
    try (final InputStream aIS = ISO10646Collections.class.getResourceAsStream (sPath))
    {
      if (aIS == null)
        throw new IllegalStateException ("Missing resource '" + sPath + "'");
      try (final NonBlockingBufferedReader aBR = new NonBlockingBufferedReader (new InputStreamReader (aIS,
                                                                                                       StandardCharsets.UTF_8)))
      {
        String sLine;
        while ((sLine = aBR.readLine ()) != null)
        {
          if (sLine.isEmpty () || sLine.startsWith ("#"))
            continue;
          final String [] aParts = sLine.split ("\\|", 3);
          if (aParts.length != 3)
            continue;
          final Integer nNumber = Integer.valueOf (aParts[0]);
          final String sName = aParts[1];
          final String sValue = aParts[2];
          if (nNumber.intValue () != 0)
            aByNumber.putIfAbsent (nNumber, sValue);
          if (!sName.isEmpty ())
            aByName.putIfAbsent (sName, sValue);
        }
      }
    }
    catch (final IOException ex)
    {
      throw new IllegalStateException ("Failed to load resource '" + sPath + "'", ex);
    }
  }

  /**
   * @param sName
   *        Inline collection name.
   * @return The raw range spec, or <code>null</code> if not found.
   */
  @Nullable
  public static String lookupInlineByName (@NonNull final String sName)
  {
    return INLINE_BY_NAME.get (sName);
  }

  /**
   * @param nNumber
   *        Inline collection number.
   * @return The raw range spec, or <code>null</code> if not found.
   */
  @Nullable
  public static String lookupInlineByNumber (final int nNumber)
  {
    return INLINE_BY_NUMBER.get (Integer.valueOf (nNumber));
  }

  /**
   * @param sName
   *        Out-of-line collection name.
   * @return The classpath resource path, or <code>null</code> if not found.
   */
  @Nullable
  public static String lookupOutOfLineResource (@NonNull final String sName)
  {
    final String sFile = OUTOFLINE_BY_NAME.get (sName);
    return sFile == null ? null : BASE + sFile;
  }

  /**
   * @param nNumber
   *        Out-of-line collection number.
   * @return The classpath resource path, or <code>null</code> if not found.
   */
  @Nullable
  public static String lookupOutOfLineResource (final int nNumber)
  {
    final String sFile = OUTOFLINE_BY_NUMBER.get (Integer.valueOf (nNumber));
    return sFile == null ? null : BASE + sFile;
  }

  /**
   * @param sName
   *        Collection name defined as a CREPDL script.
   * @return The classpath resource path of the CREPDL XML, or <code>null</code> if not found.
   */
  @Nullable
  public static String lookupCREPDLScriptResource (@NonNull final String sName)
  {
    final String sFile = CREPDL_BY_NAME.get (sName);
    return sFile == null ? null : CREPDL_BASE + sFile;
  }

  /**
   * @param nNumber
   *        Collection number defined as a CREPDL script.
   * @return The classpath resource path of the CREPDL XML, or <code>null</code> if not found.
   */
  @Nullable
  public static String lookupCREPDLScriptResource (final int nNumber)
  {
    final String sFile = CREPDL_BY_NUMBER.get (Integer.valueOf (nNumber));
    return sFile == null ? null : CREPDL_BASE + sFile;
  }
}
