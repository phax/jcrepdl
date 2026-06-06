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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.NonNull;

import com.helger.crepdl.EThreeValuedBoolean;

/**
 * Repertoires for the Ideographic Variation Database (IVD). For each
 * registered IVD collection, the bundled <code>IVD_Sequences.txt</code> is
 * scanned for base-character + selector pairs that belong to that collection.
 *
 * @author Philip Helger
 */
public final class IVDRepertoires
{
  private static final String IVD_RESOURCE = "/external/crepdl/repo/IVD/IVD_Sequences.txt";

  private IVDRepertoires ()
  {}

  // ----------------------------------------------------------------------
  // Private helpers
  // ----------------------------------------------------------------------

  @NonNull
  private static Map <Integer, Set <Integer>> _loadBaseSetPerSelector (@NonNull final String sCollectionName)
  {
    try (final InputStream aIS = IVDRepertoires.class.getResourceAsStream (IVD_RESOURCE))
    {
      if (aIS == null)
        throw new IllegalStateException ("IVD_Sequences.txt resource is missing");
      final BufferedReader aBR = new BufferedReader (new InputStreamReader (aIS, StandardCharsets.UTF_8));
      final Map <Integer, Set <Integer>> aRet = new HashMap <> ();
      String sLine;
      boolean bFoundAny = false;
      while ((sLine = aBR.readLine ()) != null)
      {
        if (sLine.startsWith ("#") || sLine.isEmpty ())
          continue;
        final String [] aSemi = sLine.split (";");
        if (aSemi.length != 3)
          throw new IllegalStateException ("Syntax error in IVD_Sequences.txt line '" + sLine + "'");
        final String sCollection = aSemi[1].trim ();
        if (!sCollection.equals (sCollectionName))
          continue;
        bFoundAny = true;
        final String [] aSeq = aSemi[0].trim ().split (" ");
        if (aSeq.length != 2)
          throw new IllegalStateException ("Syntax error in IVD_Sequences.txt line '" + sLine + "'");
        final int nBase = Integer.parseInt (aSeq[0], 16);
        final int nSelector = Integer.parseInt (aSeq[1], 16);
        aRet.computeIfAbsent (Integer.valueOf (nSelector), k -> new HashSet <> ()).add (Integer.valueOf (nBase));
      }
      if (!bFoundAny)
        throw new IllegalStateException ("IVD Collection '" + sCollectionName + "' is not registered");
      return aRet;
    }
    catch (final IOException ex)
    {
      throw new IllegalStateException ("Failed to load IVD data", ex);
    }
  }

  // ----------------------------------------------------------------------
  // Public API
  // ----------------------------------------------------------------------

  /**
   * Build a repertoire that accepts a 2-codepoint sequence
   * <code>base + selector</code> if and only if it is registered under the
   * given IVD collection.
   *
   * @param sCollectionName
   *        IVD collection name (e.g. "Adobe-Japan1"). Never <code>null</code>.
   * @return A new repertoire. Never <code>null</code>.
   * @throws IllegalStateException
   *         if the IVD data is unavailable or the collection is unregistered.
   */
  @NonNull
  public static IRepertoire create (@NonNull final String sCollectionName)
  {
    final Map <Integer, Set <Integer>> aBaseSetPerSelector = _loadBaseSetPerSelector (sCollectionName);
    return new IVDRepertoire (aBaseSetPerSelector);
  }

  // ----------------------------------------------------------------------
  // Private nested types
  // ----------------------------------------------------------------------

  private static final class IVDRepertoire implements IRepertoire
  {
    private final Map <Integer, Set <Integer>> m_aBaseSetPerSelector;

    IVDRepertoire (@NonNull final Map <Integer, Set <Integer>> aBaseSetPerSelector)
    {
      m_aBaseSetPerSelector = aBaseSetPerSelector;
    }

    @Override
    @NonNull
    public EThreeValuedBoolean check (@NonNull final String sChar)
    {
      if (sChar.length () < 3)
        return EThreeValuedBoolean.FALSE;
      final int nSelector = sChar.codePointAt (sChar.length () - 2);
      if (nSelector < 0xE0100 || nSelector > 0xE0120)
        return EThreeValuedBoolean.FALSE;
      final int nBase = sChar.codePointAt (0);
      final Set <Integer> aBases = m_aBaseSetPerSelector.get (Integer.valueOf (nSelector));
      if (aBases == null)
        return EThreeValuedBoolean.FALSE;
      return aBases.contains (Integer.valueOf (nBase)) ? EThreeValuedBoolean.TRUE : EThreeValuedBoolean.FALSE;
    }
  }
}
