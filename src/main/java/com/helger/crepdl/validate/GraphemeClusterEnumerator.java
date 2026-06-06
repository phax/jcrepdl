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
import java.io.Reader;
import java.io.UncheckedIOException;
import java.text.BreakIterator;
import java.util.Iterator;
import java.util.Locale;

import org.jspecify.annotations.NonNull;

/**
 * Splits a {@link Reader} into Unicode grapheme clusters using
 * {@link BreakIterator#getCharacterInstance(Locale)}. Mirrors
 * <code>ReadGraphemeCluster.fs</code>: read the entire stream, then iterate.
 *
 * @author Philip Helger
 */
public final class GraphemeClusterEnumerator implements Iterator <String>
{
  private final String m_sContent;
  private final BreakIterator m_aIter;
  private int m_nStart;
  private int m_nEnd;

  /**
   * @param aReader
   *        Source. Not closed by this class.
   */
  public GraphemeClusterEnumerator (@NonNull final Reader aReader)
  {
    m_sContent = _readAll (aReader);
    m_aIter = BreakIterator.getCharacterInstance (Locale.ROOT);
    m_aIter.setText (m_sContent);
    m_nStart = m_aIter.first ();
    m_nEnd = m_aIter.next ();
  }

  @NonNull
  private static String _readAll (@NonNull final Reader aReader)
  {
    try
    {
      final StringBuilder aSB = new StringBuilder ();
      final char [] aBuf = new char [4096];
      int n;
      while ((n = aReader.read (aBuf)) >= 0)
        aSB.append (aBuf, 0, n);
      return aSB.toString ();
    }
    catch (final IOException ex)
    {
      throw new UncheckedIOException (ex);
    }
  }

  @Override
  public boolean hasNext ()
  {
    return m_nEnd != BreakIterator.DONE;
  }

  @Override
  @NonNull
  public String next ()
  {
    if (!hasNext ())
      throw new java.util.NoSuchElementException ();
    final String sRet = m_sContent.substring (m_nStart, m_nEnd);
    m_nStart = m_nEnd;
    m_nEnd = m_aIter.next ();
    return sRet;
  }
}
