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
import java.util.Iterator;

import org.jspecify.annotations.NonNull;

/**
 * Reads a stream of Unicode characters, yielding each non-surrogate as a
 * single-char string and each surrogate pair as a two-char string. Mirrors
 * <code>ReadCharacter.fs</code>.
 *
 * @author Philip Helger
 */
public final class CharacterEnumerator implements Iterator <String>
{
  private final Reader m_aReader;
  private String m_sNext;
  private boolean m_bEof;

  /**
   * @param aReader
   *        Source. Never <code>null</code>. Not closed by this class.
   */
  public CharacterEnumerator (@NonNull final Reader aReader)
  {
    m_aReader = aReader;
  }

  @Override
  public boolean hasNext ()
  {
    if (m_sNext != null)
      return true;
    if (m_bEof)
      return false;
    try
    {
      final int c1 = m_aReader.read ();
      if (c1 < 0)
      {
        m_bEof = true;
        return false;
      }
      final char ch1 = (char) c1;
      if (Character.isSurrogate (ch1))
      {
        final int c2 = m_aReader.read ();
        if (c2 < 0)
        {
          m_sNext = String.valueOf (ch1);
          m_bEof = true;
        }
        else
        {
          m_sNext = new String (new char [] { ch1, (char) c2 });
        }
      }
      else
      {
        m_sNext = String.valueOf (ch1);
      }
      return true;
    }
    catch (final IOException ex)
    {
      throw new UncheckedIOException (ex);
    }
  }

  @Override
  @NonNull
  public String next ()
  {
    if (!hasNext ())
      throw new java.util.NoSuchElementException ();
    final String s = m_sNext;
    m_sNext = null;
    return s;
  }
}
