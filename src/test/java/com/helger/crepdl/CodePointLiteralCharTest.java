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
package com.helger.crepdl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.helger.crepdl.validate.CREPDLStreamValidationResult;
import com.helger.crepdl.validate.CREPDLValidator;

/**
 * Tests for the ISO/IEC 19757-7:2020 code-point-literal <code>&lt;char&gt;</code>
 * content syntax (single point <code>U+XXXX</code> and range
 * <code>U+XXXX-U+YYYY</code>).
 *
 * @author Philip Helger
 */
final class CodePointLiteralCharTest
{
  private static final String NS = " xmlns=\"http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0\"";

  @Test
  void testSingleCodePoint ()
  {
    final String sXml = "<char" + NS + ">U+0041</char>";
    final CREPDLValidator v = CREPDLValidator.createFromString (sXml, null);
    assertEquals (ECREPDLValidationResult.TRUE, v.validateCharacter ("A"));
    assertEquals (ECREPDLValidationResult.FALSE, v.validateCharacter ("B"));
  }

  @Test
  void testCodePointRange ()
  {
    final String sXml = "<char" + NS + ">U+0020-U+007E</char>";
    final CREPDLValidator v = CREPDLValidator.createFromString (sXml, null);
    assertEquals (ECREPDLValidationResult.TRUE, v.validateCharacter (" "));
    assertEquals (ECREPDLValidationResult.TRUE, v.validateCharacter ("A"));
    assertEquals (ECREPDLValidationResult.TRUE, v.validateCharacter ("~"));
    assertEquals (ECREPDLValidationResult.FALSE, v.validateCharacter ("\t"));
    assertEquals (ECREPDLValidationResult.FALSE, v.validateCharacter ("À"));
  }

  @Test
  void testCodePointMultipleTokens ()
  {
    final String sXml = "<char" + NS + ">U+0009 U+000A U+0020-U+007E</char>";
    final CREPDLValidator v = CREPDLValidator.createFromString (sXml, null);
    assertEquals (ECREPDLValidationResult.TRUE, v.validateCharacter ("\t"));
    assertEquals (ECREPDLValidationResult.TRUE, v.validateCharacter ("\n"));
    assertEquals (ECREPDLValidationResult.TRUE, v.validateCharacter ("A"));
    assertEquals (ECREPDLValidationResult.FALSE, v.validateCharacter ("\r"));
  }

  @Test
  void testSupplementaryCodePoint ()
  {
    final String sXml = "<char" + NS + ">U+1F30D</char>";
    final CREPDLValidator v = CREPDLValidator.createFromString (sXml, null);
    // U+1F30D requires a 2-char surrogate pair
    final String sEarth = new String (Character.toChars (0x1F30D));
    assertEquals (ECREPDLValidationResult.TRUE, v.validateCharacter (sEarth));
    assertEquals (ECREPDLValidationResult.FALSE, v.validateCharacter ("A"));
  }

  @Test
  void testKernelInCodePointSyntax ()
  {
    final String sXml = "<char" + NS + "><kernel>U+0041-U+005A</kernel></char>";
    final CREPDLValidator v = CREPDLValidator.createFromString (sXml, null);
    assertEquals (ECREPDLValidationResult.TRUE, v.validateCharacter ("A"));
    assertEquals (ECREPDLValidationResult.UNKNOWN, v.validateCharacter ("a"));
  }

  @Test
  void testKernelAndHullInCodePointSyntax ()
  {
    final String sXml = "<char" + NS + ">" +
                        "<kernel>U+0041-U+005A</kernel>" +
                        "<hull>U+0041-U+007A</hull>" +
                        "</char>";
    final CREPDLValidator v = CREPDLValidator.createFromString (sXml, null);
    assertEquals (ECREPDLValidationResult.TRUE, v.validateCharacter ("A"));
    assertEquals (ECREPDLValidationResult.UNKNOWN, v.validateCharacter ("a"));
    assertEquals (ECREPDLValidationResult.FALSE, v.validateCharacter ("0"));
  }

  @Test
  void testRegexStillWorks ()
  {
    // Regex content not in code-point-literal syntax should still compile as regex
    final String sXml = "<char" + NS + ">[A-Z]</char>";
    final CREPDLValidator v = CREPDLValidator.createFromString (sXml, null);
    assertEquals (ECREPDLValidationResult.TRUE, v.validateCharacter ("A"));
    assertEquals (ECREPDLValidationResult.FALSE, v.validateCharacter ("a"));
  }

  /**
   * The full sample schema from the SignalArc CREPDL&middot;CHECK validator
   * (<a href="https://apps.signalarc.com/crepdl-validator/">apps.signalarc.com/crepdl-validator</a>).
   * Run their sample text through our validator and assert that it identifies
   * exactly the same two violating characters
   * (<code>U+65E5</code> and <code>U+672C</code>) that the SignalArc service
   * reports.
   */
  @Test
  void testSignalArcSampleSchemaOnSignalArcSampleText ()
  {
    final String sXml = "<union" + NS + ">" +
                        "  <char>U+0009</char>" +
                        "  <char>U+000A</char>" +
                        "  <char>U+000D</char>" +
                        "  <char>U+0020-U+007E</char>" +
                        "  <char>U+00A0</char>" +
                        "  <char>U+00C0-U+00FF</char>" +
                        "  <char>U+2013-U+2014</char>" +
                        "  <char>U+2018-U+201E</char>" +
                        "  <char>U+2026</char>" +
                        "</union>";
    final CREPDLValidator v = CREPDLValidator.createFromString (sXml, null);
    final String sText = "Dia dhuit! Café — résumé… 日本";
    final CREPDLStreamValidationResult r = v.validateString (sText);
    assertEquals (0, r.unknowns ().length);
    assertEquals (2, r.notIncluded ().length);
    assertEquals ("日", r.notIncluded ()[0]);
    assertEquals ("本", r.notIncluded ()[1]);
  }
}
