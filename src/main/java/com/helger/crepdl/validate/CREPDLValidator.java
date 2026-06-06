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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.crepdl.CCREPDL;
import com.helger.crepdl.ECREPDLValidationResult;
import com.helger.crepdl.EMode;
import com.helger.crepdl.EThreeValuedBoolean;
import com.helger.crepdl.model.ICREPDLNode;
import com.helger.crepdl.parse.CREPDLReader;

/**
 * Main entry point of this library. A {@link CREPDLValidator} caches the
 * expanded CREPDL tree plus pre-built repertoires for fast repeated checking.
 *
 * <p>
 * Per-string results are memoized with a simple bounded cache (3000 entries,
 * mirroring the F# reference).
 * </p>
 *
 * @author Philip Helger
 */
public final class CREPDLValidator
{
  private static final int CACHE_SIZE = 3000;

  private final ICREPDLNode m_aExpandedRoot;
  private final EMode m_eRootMode;
  private final StringChecker m_aChecker;
  private final Map <String, EThreeValuedBoolean> m_aCache = new HashMap <> ();

  private CREPDLValidator (@NonNull final ICREPDLNode aExpandedRoot)
  {
    m_aExpandedRoot = aExpandedRoot;
    m_eRootMode = aExpandedRoot.getMode () == null ? EMode.CHARACTER : aExpandedRoot.getMode ();
    m_aChecker = new StringChecker (RegistryRepertoireDictionary.build (aExpandedRoot));
  }

  // ----------------------------------------------------------------------
  // Private helpers
  // ----------------------------------------------------------------------

  @NonNull
  private EThreeValuedBoolean _check (@NonNull final String sChar)
  {
    final EThreeValuedBoolean eHit = m_aCache.get (sChar);
    if (eHit != null)
      return eHit;
    final EThreeValuedBoolean eRes = m_aChecker.check (m_aExpandedRoot,
                                                       sChar,
                                                       CCREPDL.DEFAULT_MIN_VERSION_INT,
                                                       CCREPDL.DEFAULT_MAX_VERSION_INT);
    if (m_aCache.size () < CACHE_SIZE)
      m_aCache.put (sChar, eRes);
    return eRes;
  }

  // ----------------------------------------------------------------------
  // Public factory methods
  // ----------------------------------------------------------------------

  /**
   * @param aRoot
   *        Already-parsed CREPDL root. Never <code>null</code>.
   * @return A fresh validator. Never <code>null</code>.
   */
  @NonNull
  public static CREPDLValidator create (@NonNull final ICREPDLNode aRoot)
  {
    return new CREPDLValidator (RefAndRepertoireExpander.expand (aRoot));
  }

  /**
   * @param aFile
   *        CREPDL script file. Never <code>null</code>.
   * @return A fresh validator.
   */
  @NonNull
  public static CREPDLValidator create (@NonNull final File aFile)
  {
    return create (CREPDLReader.readScript (aFile.toURI ()));
  }

  /**
   * @param aUri
   *        Absolute URI of the script. Never <code>null</code>.
   * @return A fresh validator.
   */
  @NonNull
  public static CREPDLValidator create (@NonNull final URI aUri)
  {
    return create (CREPDLReader.readScript (aUri));
  }

  /**
   * @param sXml
   *        XML source. Never <code>null</code>.
   * @param aBaseUri
   *        Optional base URI for ref resolution.
   * @return A fresh validator.
   */
  @NonNull
  public static CREPDLValidator createFromString (@NonNull final String sXml, @Nullable final URI aBaseUri)
  {
    return create (CREPDLReader.readScriptFromString (sXml, aBaseUri));
  }

  // ----------------------------------------------------------------------
  // Public API
  // ----------------------------------------------------------------------

  /**
   * @return The mode declared on the root element. Defaults to
   *         {@link EMode#CHARACTER}.
   */
  @NonNull
  public EMode getRootMode ()
  {
    return m_eRootMode;
  }

  /**
   * Validate a single Unicode character.
   *
   * @param sCharStr
   *        A string of length 1 (BMP) or 2 (surrogate pair).
   * @return The validation result.
   * @throws IllegalStateException
   *         if the script's root mode is not {@link EMode#CHARACTER}.
   */
  @NonNull
  public ECREPDLValidationResult validateCharacter (@NonNull final String sCharStr)
  {
    if (m_eRootMode != EMode.CHARACTER)
      throw new IllegalStateException ("The root mode is not CHARACTER");
    return _check (sCharStr).toValidationResult ();
  }

  /**
   * Validate a single Unicode grapheme cluster.
   *
   * @param sGcStr
   *        A grapheme cluster.
   * @return The validation result.
   * @throws IllegalStateException
   *         if the script's root mode is not {@link EMode#GRAPHEME_CLUSTER}.
   */
  @NonNull
  public ECREPDLValidationResult validateGraphemeCluster (@NonNull final String sGcStr)
  {
    if (m_eRootMode != EMode.GRAPHEME_CLUSTER)
      throw new IllegalStateException ("The root mode is not GRAPHEME_CLUSTER");
    return _check (sGcStr).toValidationResult ();
  }

  /**
   * Validate the text from a {@link Reader}. Iteration mode is governed by the
   * script's root mode.
   *
   * @param aReader
   *        The source. Not closed by this method.
   * @return Pair of arrays (unknowns; not-included).
   */
  @NonNull
  public CREPDLStreamValidationResult validateTextStream (@NonNull final Reader aReader)
  {
    final List <String> aUnknowns = new ArrayList <> ();
    final List <String> aNotIncluded = new ArrayList <> ();
    final Iterator <String> aIt = switch (m_eRootMode)
    {
      case CHARACTER -> new CharacterEnumerator (aReader);
      case GRAPHEME_CLUSTER -> new GraphemeClusterEnumerator (aReader);
    };
    while (aIt.hasNext ())
    {
      final String sUnit = aIt.next ();
      switch (_check (sUnit))
      {
        case TRUE:
          break;
        case UNKNOWN:
          aUnknowns.add (sUnit);
          break;
        case FALSE:
          aNotIncluded.add (sUnit);
          break;
      }
    }
    return new CREPDLStreamValidationResult (aUnknowns.toArray (new String [0]),
                                             aNotIncluded.toArray (new String [0]));
  }

  /**
   * Validate the given in-memory string.
   *
   * @param sText
   *        The string. Never <code>null</code>.
   * @return Pair of arrays (unknowns; not-included).
   */
  @NonNull
  public CREPDLStreamValidationResult validateString (@NonNull final String sText)
  {
    return validateTextStream (new StringReader (sText));
  }

  /**
   * Validate the given file, read using the given charset.
   *
   * @param aFile
   *        File to read.
   * @param sCharsetName
   *        Charset name passed to {@link Charset#forName(String)}.
   * @return Pair of arrays (unknowns; not-included).
   * @throws IOException
   *         on I/O error.
   * @throws IllegalArgumentException
   *         if the charset is not supported.
   */
  @NonNull
  public CREPDLStreamValidationResult validateFile (@NonNull final File aFile,
                                                    @NonNull final String sCharsetName) throws IOException
  {
    final Charset aCharset;
    try
    {
      aCharset = Charset.forName (sCharsetName);
    }
    catch (final UnsupportedCharsetException ex)
    {
      throw new IllegalArgumentException (sCharsetName + ": illegal encoding name", ex);
    }
    try (final InputStream aIS = new FileInputStream (aFile);
         final Reader aReader = new BufferedReader (new InputStreamReader (aIS, aCharset)))
    {
      return validateTextStream (aReader);
    }
  }
}
