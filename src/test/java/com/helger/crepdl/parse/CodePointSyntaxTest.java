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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CodePointSyntaxTest
{
  @Test
  void testSingleCodePoint ()
  {
    assertEquals ("[\\x{0009}]", CodePointSyntax.toRegexOrNull ("U+0009"));
    assertEquals ("[\\x{0020}]", CodePointSyntax.toRegexOrNull ("U+0020"));
    assertEquals ("[\\x{1F30D}]", CodePointSyntax.toRegexOrNull ("U+1F30D"));
  }

  @Test
  void testRange ()
  {
    assertEquals ("[\\x{0020}-\\x{007E}]", CodePointSyntax.toRegexOrNull ("U+0020-U+007E"));
    assertEquals ("[\\x{00C0}-\\x{00FF}]", CodePointSyntax.toRegexOrNull ("U+00C0-U+00FF"));
  }

  @Test
  void testWhitespaceUnion ()
  {
    assertEquals ("[\\x{0009}\\x{000A}\\x{0020}-\\x{007E}]",
                  CodePointSyntax.toRegexOrNull ("U+0009 U+000A U+0020-U+007E"));
  }

  @Test
  void testTrimmingAndCase ()
  {
    assertEquals ("[\\x{00FF}]", CodePointSyntax.toRegexOrNull ("  U+00FF  "));
    assertEquals ("[\\x{00ff}]", CodePointSyntax.toRegexOrNull ("U+00ff"));
  }

  @Test
  void testNonCodePointFormsReturnNull ()
  {
    assertNull (CodePointSyntax.toRegexOrNull (null));
    assertNull (CodePointSyntax.toRegexOrNull (""));
    assertNull (CodePointSyntax.toRegexOrNull ("   "));
    // Looks like a regex: square brackets, escapes
    assertNull (CodePointSyntax.toRegexOrNull ("[A-Z]"));
    assertNull (CodePointSyntax.toRegexOrNull ("\\p{L}"));
    assertNull (CodePointSyntax.toRegexOrNull ("A"));
    // Almost the right shape but not quite
    assertNull (CodePointSyntax.toRegexOrNull ("U+"));
    assertNull (CodePointSyntax.toRegexOrNull ("U+ZZZZ"));
    assertNull (CodePointSyntax.toRegexOrNull ("U+0020 garbage"));
    assertNull (CodePointSyntax.toRegexOrNull ("U+0020-"));
    // Reversed range
    assertNull (CodePointSyntax.toRegexOrNull ("U+007E-U+0020"));
    // Out of Unicode range
    assertNull (CodePointSyntax.toRegexOrNull ("U+110000"));
  }

  @Test
  void testIsCodePointLiteralSyntaxFlag ()
  {
    assertTrue (CodePointSyntax.isCodePointLiteralSyntax ("U+0020"));
    assertTrue (CodePointSyntax.isCodePointLiteralSyntax ("U+0020-U+007E"));
    assertFalse (CodePointSyntax.isCodePointLiteralSyntax ("[A-Z]"));
    assertFalse (CodePointSyntax.isCodePointLiteralSyntax (null));
  }

  @Test
  void testPassThrough ()
  {
    assertEquals ("[\\x{0020}-\\x{007E}]", CodePointSyntax.translateOrPassThrough ("U+0020-U+007E"));
    // Regex passes through unchanged
    assertEquals ("[A-Z]", CodePointSyntax.translateOrPassThrough ("[A-Z]"));
  }
}
