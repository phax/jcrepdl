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
import java.io.Reader;
import java.util.Arrays;

import org.jspecify.annotations.NonNull;

import com.helger.crepdl.EThreeValuedBoolean;

/**
 * A {@link IRepertoire} backed by a sorted, disjoint array of code-point
 * ranges. Membership is decided with a binary search.
 *
 * @author Philip Helger
 */
public final class CodePointRangeRepertoire implements IRepertoire
{
  private final int [] m_aStarts;
  private final int [] m_aEnds;

  /**
   * @param aStarts
   *        Inclusive range starts. Must be sorted and disjoint with respect to
   *        <code>aEnds</code>. Same length as <code>aEnds</code>.
   * @param aEnds
   *        Inclusive range ends.
   */
  public CodePointRangeRepertoire (@NonNull final int [] aStarts, @NonNull final int [] aEnds)
  {
    if (aStarts.length != aEnds.length)
      throw new IllegalArgumentException ("starts/ends length mismatch");
    m_aStarts = aStarts;
    m_aEnds = aEnds;
  }

  // ----------------------------------------------------------------------
  // Private helpers (no internal dependencies first)
  // ----------------------------------------------------------------------

  private static int _parseCodePoint (@NonNull final String s)
  {
    if (s.startsWith ("0x") || s.startsWith ("0X"))
      return Integer.parseInt (s.substring (2), 16);
    return Integer.parseInt (s);
  }

  @NonNull
  private static CodePointRangeRepertoire _build (@NonNull final int [] aStarts, @NonNull final int [] aEnds)
  {
    final Integer [] aIdx = new Integer [aStarts.length];
    for (int i = 0; i < aIdx.length; i++)
      aIdx[i] = Integer.valueOf (i);
    Arrays.sort (aIdx, (a, b) -> Integer.compare (aStarts[a.intValue ()], aStarts[b.intValue ()]));
    final int [] aSortedStarts = new int [aStarts.length];
    final int [] aSortedEnds = new int [aEnds.length];
    for (int i = 0; i < aIdx.length; i++)
    {
      aSortedStarts[i] = aStarts[aIdx[i].intValue ()];
      aSortedEnds[i] = aEnds[aIdx[i].intValue ()];
    }
    return new CodePointRangeRepertoire (aSortedStarts, aSortedEnds);
  }

  private boolean _contains (final int nCp)
  {
    int nLo = 0;
    int nHi = m_aStarts.length - 1;
    while (nLo <= nHi)
    {
      final int nMid = (nLo + nHi) >>> 1;
      if (nCp < m_aStarts[nMid])
        nHi = nMid - 1;
      else if (nCp > m_aEnds[nMid])
        nLo = nMid + 1;
      else
        return true;
    }
    return false;
  }

  // ----------------------------------------------------------------------
  // Public API
  // ----------------------------------------------------------------------

  /**
   * Build a repertoire by reading a text file of "start,end" or "single"
   * codepoint entries, one per line. Each number may be decimal or in
   * <code>0x...</code> hexadecimal form (matching the F# reference's
   * <code>int</code> parsing). A leading UTF-8/UTF-16 BOM is stripped.
   *
   * @param aReader
   *        Source to read; closed by the caller.
   * @return A new repertoire.
   * @throws IOException
   *         on read failure.
   */
  @NonNull
  public static CodePointRangeRepertoire readDecimalRanges (@NonNull final Reader aReader) throws IOException
  {
    final BufferedReader aBR = new BufferedReader (aReader);
    int [] aStarts = new int [128];
    int [] aEnds = new int [128];
    int nCount = 0;
    String sLine;
    boolean bFirst = true;
    while ((sLine = aBR.readLine ()) != null)
    {
      String sTrim = sLine.trim ();
      if (bFirst)
      {
        if (!sTrim.isEmpty () && sTrim.charAt (0) == '﻿')
          sTrim = sTrim.substring (1).trim ();
        bFirst = false;
      }
      if (sTrim.isEmpty () || sTrim.startsWith ("#"))
        continue;
      final int nComma = sTrim.indexOf (',');
      final int nStart;
      final int nEnd;
      if (nComma < 0)
      {
        nStart = _parseCodePoint (sTrim);
        nEnd = nStart;
      }
      else
      {
        nStart = _parseCodePoint (sTrim.substring (0, nComma).trim ());
        nEnd = _parseCodePoint (sTrim.substring (nComma + 1).trim ());
      }
      if (nCount == aStarts.length)
      {
        aStarts = Arrays.copyOf (aStarts, aStarts.length * 2);
        aEnds = Arrays.copyOf (aEnds, aEnds.length * 2);
      }
      aStarts[nCount] = nStart;
      aEnds[nCount] = nEnd;
      nCount++;
    }
    return _build (Arrays.copyOf (aStarts, nCount), Arrays.copyOf (aEnds, nCount));
  }

  /**
   * Build a repertoire by reading semi-colon separated ranges (the storage
   * format of the bundled <code>iso10646-inline.txt</code> file).
   *
   * @param sSpec
   *        Specification string, e.g. <code>"32,126;128,255"</code> or
   *        <code>""</code>.
   * @return A new repertoire.
   */
  @NonNull
  public static CodePointRangeRepertoire fromSemicolonSpec (@NonNull final String sSpec)
  {
    if (sSpec.isEmpty ())
      return new CodePointRangeRepertoire (new int [0], new int [0]);
    final String [] aRanges = sSpec.split (";");
    final int [] aStarts = new int [aRanges.length];
    final int [] aEnds = new int [aRanges.length];
    for (int i = 0; i < aRanges.length; i++)
    {
      final String sRange = aRanges[i];
      final int nComma = sRange.indexOf (',');
      if (nComma < 0)
      {
        final int n = Integer.parseInt (sRange);
        aStarts[i] = n;
        aEnds[i] = n;
      }
      else
      {
        aStarts[i] = Integer.parseInt (sRange.substring (0, nComma));
        aEnds[i] = Integer.parseInt (sRange.substring (nComma + 1));
      }
    }
    return _build (aStarts, aEnds);
  }

  @Override
  @NonNull
  public EThreeValuedBoolean check (@NonNull final String sChar)
  {
    if (sChar.isEmpty ())
      return EThreeValuedBoolean.FALSE;
    final int nCp = sChar.codePointAt (0);
    if (Character.charCount (nCp) != sChar.length ())
      return EThreeValuedBoolean.FALSE;
    return _contains (nCp) ? EThreeValuedBoolean.TRUE : EThreeValuedBoolean.FALSE;
  }
}
