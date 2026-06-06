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
package com.helger.crepdl.parse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.string.StringParser;

/**
 * Detects and translates the ISO/IEC 19757-7:2020 code-point-literal syntax used inside
 * <code>&lt;char&gt;</code> (and <code>&lt;kernel&gt;</code> / <code>&lt;hull&gt;</code>) elements.
 * <p>
 * The 2020 revision narrowed <code>&lt;char&gt;</code> content from "an ICU regular expression" (as
 * in the F# v2.0 reference) to "one or more code-point literals or ranges":
 * </p>
 * <ul>
 * <li><code>U+XXXX</code> &mdash; a single code point in hex</li>
 * <li><code>U+XXXX-U+YYYY</code> &mdash; an inclusive code-point range</li>
 * <li>Multiple of the above separated by whitespace &mdash; treated as union</li>
 * </ul>
 * <p>
 * This helper detects that shape and translates it to an equivalent Java regex character class
 * using <code>\x{...}</code> escapes. The resulting pattern can then be evaluated by the same
 * {@link com.helger.crepdl.validate.CharMatcher} that handles native regex content.
 * </p>
 * <p>
 * Safe to apply by default: a regex source that <em>literally</em> contains only
 * <code>U+XXXX</code> tokens cannot match any single Unicode character (it tries to match the
 * 6-character string "U+XXXX"), so reinterpreting such content as code-point-literal syntax
 * strictly improves behaviour.
 * </p>
 *
 * @author Philip Helger
 */
public final class CodePointSyntax
{
  private static final Pattern TOKEN_PATTERN = Pattern.compile ("U\\+([0-9A-Fa-f]{1,6})(?:-U\\+([0-9A-Fa-f]{1,6}))?");
  private static final Pattern TOKEN_SPACES = Pattern.compile ("\\s+");
  private static final int MAX_CODE_POINT = 0x10FFFF;

  private CodePointSyntax ()
  {}

  private static boolean _isValidCp (final int nCp)
  {
    return nCp >= 0 && nCp <= MAX_CODE_POINT;
  }

  /**
   * Try to translate the given content into a Java regex character class.
   *
   * @param sContent
   *        The raw text content of a <code>&lt;char&gt;</code> / <code>&lt;kernel&gt;</code> /
   *        <code>&lt;hull&gt;</code> element. May be <code>null</code>.
   * @return The equivalent Java regex (e.g. <code>"[\\x{0020}-\\x{007E}]"</code>) if the content is
   *         in code-point-literal syntax, or <code>null</code> if it is not (caller should fall
   *         back to treating the content as a regex).
   */
  @Nullable
  public static String toRegexOrNull (@Nullable final String sContent)
  {
    if (sContent == null)
      return null;

    final String sTrimmed = sContent.trim ();
    if (sTrimmed.isEmpty ())
      return null;

    final String [] aTokens = TOKEN_SPACES.split (sTrimmed, 0);
    final StringBuilder aCls = new StringBuilder ("[");
    for (final String sTok : aTokens)
    {
      final Matcher aM = TOKEN_PATTERN.matcher (sTok);
      if (!aM.matches ())
        return null;

      final String sStart = aM.group (1);
      final String sEnd = aM.group (2);
      final int nStart = StringParser.parseInt (sStart, 16, -1);
      if (nStart < 0)
        return null;

      if (!_isValidCp (nStart))
        return null;

      aCls.append ("\\x{").append (sStart).append ("}");
      if (sEnd != null)
      {
        final int nEnd = StringParser.parseInt (sEnd, 16, -1);
        if (nEnd < 0)
          return null;

        if (!_isValidCp (nEnd) || nEnd < nStart)
          return null;

        aCls.append ("-\\x{").append (sEnd).append ("}");
      }
    }
    aCls.append (']');
    return aCls.toString ();
  }

  /**
   * @param sContent
   *        Raw element content. May be <code>null</code>.
   * @return <code>true</code> iff {@link #toRegexOrNull(String)} would return a
   *         non-<code>null</code> value.
   */
  public static boolean isCodePointLiteralSyntax (@Nullable final String sContent)
  {
    return toRegexOrNull (sContent) != null;
  }

  /**
   * Convenience: return the translated regex if the content is in code-point literal syntax,
   * otherwise return the content unchanged.
   *
   * @param sContent
   *        Raw element content.
   * @return Translated regex or the original content.
   */
  @NonNull
  public static String translateOrPassThrough (@NonNull final String sContent)
  {
    final String sTranslated = toRegexOrNull (sContent);
    return sTranslated != null ? sTranslated : sContent;
  }
}
