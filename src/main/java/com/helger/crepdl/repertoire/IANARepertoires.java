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

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.UnsupportedCharsetException;

import org.jspecify.annotations.NonNull;

import com.helger.crepdl.EThreeValuedBoolean;
import com.helger.crepdl.model.RegistryIANA;

/**
 * Builds a {@link IRepertoire} from an IANA charset name by round-tripping
 * each candidate string through that charset. A string is "in" the repertoire
 * iff <code>decode(encode(s))</code> equals <code>s</code> exactly.
 *
 * @author Philip Helger
 */
public final class IANARepertoires
{
  private IANARepertoires ()
  {}

  /**
   * Build a repertoire for the given IANA registry entry.
   *
   * @param aRegistry
   *        The parsed IANA registry information. Must contain a name.
   * @return A new repertoire. Never <code>null</code>.
   * @throws IllegalArgumentException
   *         if no name is provided or the name is not a supported JVM charset.
   */
  @NonNull
  public static IRepertoire create (@NonNull final RegistryIANA aRegistry)
  {
    if (aRegistry.name () == null)
      throw new IllegalArgumentException ("IANA miBenum is not supported");
    final Charset aCharset;
    try
    {
      aCharset = Charset.forName (aRegistry.name ());
    }
    catch (final UnsupportedCharsetException ex)
    {
      throw new IllegalArgumentException ("The charset name '" + aRegistry.name () + "' is not supported", ex);
    }
    return new IANARepertoire (aCharset);
  }

  private static final class IANARepertoire implements IRepertoire
  {
    private final ThreadLocal <CharsetEncoder> m_aEncoder;
    private final ThreadLocal <CharsetDecoder> m_aDecoder;

    IANARepertoire (@NonNull final Charset aCharset)
    {
      m_aEncoder = ThreadLocal.withInitial ( () -> aCharset.newEncoder ()
                                                           .onMalformedInput (CodingErrorAction.REPORT)
                                                           .onUnmappableCharacter (CodingErrorAction.REPORT));
      m_aDecoder = ThreadLocal.withInitial ( () -> aCharset.newDecoder ()
                                                           .onMalformedInput (CodingErrorAction.REPORT)
                                                           .onUnmappableCharacter (CodingErrorAction.REPORT));
    }

    @Override
    @NonNull
    public EThreeValuedBoolean check (@NonNull final String sChar)
    {
      try
      {
        final CharsetEncoder aEnc = m_aEncoder.get ();
        aEnc.reset ();
        final java.nio.ByteBuffer aBytes = aEnc.encode (java.nio.CharBuffer.wrap (sChar));
        final CharsetDecoder aDec = m_aDecoder.get ();
        aDec.reset ();
        final java.nio.CharBuffer aChars = aDec.decode (aBytes);
        return aChars.toString ().equals (sChar) ? EThreeValuedBoolean.TRUE : EThreeValuedBoolean.FALSE;
      }
      catch (final java.nio.charset.CharacterCodingException ex)
      {
        return EThreeValuedBoolean.FALSE;
      }
    }
  }
}
