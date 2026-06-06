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

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.crepdl.EThreeValuedBoolean;
import com.helger.crepdl.model.CREPDLChar;

/**
 * Evaluates {@link CREPDLChar} nodes against a candidate string. Uses
 * <code>java.util.regex.Pattern</code> with {@link Pattern#COMMENTS} and
 * {@link Pattern#UNICODE_CHARACTER_CLASS} so that whitespace in CREPDL regex
 * sources and <code>\p{...}</code> Unicode properties behave like the F#
 * reference implementation that uses ICU's <code>COMMENTS</code> regex flag.
 *
 * @author Philip Helger
 */
public final class CharMatcher
{
  private CharMatcher ()
  {}

  /**
   * Compile a CREPDL char-element regex source.
   *
   * @param sRegex
   *        Regex source text. May be <code>null</code>; the result is then
   *        also <code>null</code>.
   * @return Compiled pattern or <code>null</code>.
   * @throws IllegalArgumentException
   *         if the regex is invalid.
   */
  @Nullable
  public static Pattern compile (@Nullable final String sRegex)
  {
    if (sRegex == null)
      return null;
    try
    {
      return Pattern.compile (sRegex, Pattern.COMMENTS | Pattern.UNICODE_CHARACTER_CLASS);
    }
    catch (final PatternSyntaxException ex)
    {
      throw new IllegalArgumentException (sRegex + " is an illegal regular expression.", ex);
    }
  }

  /**
   * Evaluate a {@link CREPDLChar} against a candidate string.
   *
   * @param sChar
   *        The candidate string. Never <code>null</code>.
   * @param aChar
   *        The parsed char element.
   * @return Three-valued result. Never <code>null</code>.
   */
  @NonNull
  public static EThreeValuedBoolean check (@NonNull final String sChar, @NonNull final CREPDLChar aChar)
  {
    final Pattern aKernel = compile (aChar.kernelRegex ());
    final Pattern aHull = compile (aChar.hullRegex ());

    if (aKernel == null && aHull == null)
      throw new IllegalStateException ("CREPDLChar has neither kernel nor hull");

    final boolean bKernelMatches = aKernel != null && aKernel.matcher (sChar).matches ();
    final boolean bHullMatches = aHull != null && aHull.matcher (sChar).matches ();

    if (aKernel != null && aHull == null)
      return bKernelMatches ? EThreeValuedBoolean.TRUE : EThreeValuedBoolean.UNKNOWN;
    if (aKernel == null && aHull != null)
      return bHullMatches ? EThreeValuedBoolean.UNKNOWN : EThreeValuedBoolean.FALSE;
    // both non-null
    if (aChar.kernelEqualsHull ())
      return bKernelMatches ? EThreeValuedBoolean.TRUE : EThreeValuedBoolean.FALSE;
    if (bKernelMatches)
      return EThreeValuedBoolean.TRUE;
    if (!bHullMatches)
      return EThreeValuedBoolean.FALSE;
    return EThreeValuedBoolean.UNKNOWN;
  }
}
