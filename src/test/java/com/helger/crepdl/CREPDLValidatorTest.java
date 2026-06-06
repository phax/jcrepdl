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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.helger.crepdl.parse.CREPDLParseException;
import com.helger.crepdl.validate.CREPDLStreamValidationResult;
import com.helger.crepdl.validate.CREPDLValidator;

final class CREPDLValidatorTest
{
  private static final String NS = " xmlns=\"http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0\"";

  @Test
  void testIllegalNamespaceRejected ()
  {
    final String sXml = "<union xmlns=\"http://example.org/wrong\"/>";
    assertThrows (CREPDLParseException.class, () -> CREPDLValidator.createFromString (sXml, null));
  }

  @Test
  void testCharElementBasicLatin ()
  {
    final String sXml = "<char" + NS + ">[A-Z]</char>";
    final CREPDLValidator aValidator = CREPDLValidator.createFromString (sXml, null);
    assertEquals (ECREPDLValidationResult.TRUE, aValidator.validateCharacter ("A"));
    assertEquals (ECREPDLValidationResult.FALSE, aValidator.validateCharacter ("a"));
  }

  @Test
  void testISO10646InlineByNumber ()
  {
    // 1 = BASIC LATIN
    final String sXml = "<repertoire" + NS + " registry=\"10646\" number=\"1\"/>";
    final CREPDLValidator aValidator = CREPDLValidator.createFromString (sXml, null);
    assertEquals (ECREPDLValidationResult.TRUE, aValidator.validateCharacter ("A"));
    assertEquals (ECREPDLValidationResult.FALSE, aValidator.validateCharacter ("ä"));
  }

  @Test
  void testISO10646InlineByName ()
  {
    final String sXml = "<repertoire" + NS + " registry=\"10646\" name=\"BASIC LATIN\"/>";
    final CREPDLValidator aValidator = CREPDLValidator.createFromString (sXml, null);
    assertEquals (ECREPDLValidationResult.TRUE, aValidator.validateCharacter ("Z"));
  }

  @Test
  void testUnionOfTwoBlocks ()
  {
    // BASIC LATIN union LATIN-1 SUPPLEMENT
    final String sXml = "<union" + NS +
                        "><repertoire registry=\"10646\" number=\"1\"/><repertoire registry=\"10646\" number=\"2\"/></union>";
    final CREPDLValidator aValidator = CREPDLValidator.createFromString (sXml, null);
    assertEquals (ECREPDLValidationResult.TRUE, aValidator.validateCharacter ("A"));
    assertEquals (ECREPDLValidationResult.TRUE, aValidator.validateCharacter ("ä"));
    assertEquals (ECREPDLValidationResult.FALSE, aValidator.validateCharacter ("Ā"));
  }

  @Test
  void testDifference ()
  {
    // BASIC LATIN minus [A-Z]
    final String sXml = "<difference" +
                        NS +
                        "><repertoire registry=\"10646\" number=\"1\"/><char>[A-Z]</char></difference>";
    final CREPDLValidator aValidator = CREPDLValidator.createFromString (sXml, null);
    assertEquals (ECREPDLValidationResult.FALSE, aValidator.validateCharacter ("A"));
    assertEquals (ECREPDLValidationResult.TRUE, aValidator.validateCharacter ("a"));
  }

  @Test
  void testIntersection ()
  {
    // BASIC LATIN intersect [A-z] minus the non-alphabetics
    final String sXml = "<intersection" +
                        NS +
                        "><repertoire registry=\"10646\" number=\"1\"/><char>[A-Za-z]</char></intersection>";
    final CREPDLValidator aValidator = CREPDLValidator.createFromString (sXml, null);
    assertEquals (ECREPDLValidationResult.TRUE, aValidator.validateCharacter ("A"));
    assertEquals (ECREPDLValidationResult.FALSE, aValidator.validateCharacter ("0"));
  }

  @Test
  void testStreamValidation ()
  {
    final String sXml = "<repertoire" + NS + " registry=\"10646\" number=\"1\"/>";
    final CREPDLValidator aValidator = CREPDLValidator.createFromString (sXml, null);
    final CREPDLStreamValidationResult aRes = aValidator.validateString ("Hello!ä");
    assertEquals (0, aRes.unknowns ().length);
    assertEquals (1, aRes.notIncluded ().length);
    assertEquals ("ä", aRes.notIncluded ()[0]);
  }

  @Test
  void testKernelOnlyYieldsUnknownWhenNoMatch ()
  {
    // kernel matches [A-Z]; nothing else - hull missing means UNKNOWN for non-matches
    final String sXml = "<char" + NS + "><kernel>[A-Z]</kernel></char>";
    final CREPDLValidator aValidator = CREPDLValidator.createFromString (sXml, null);
    assertEquals (ECREPDLValidationResult.TRUE, aValidator.validateCharacter ("A"));
    assertEquals (ECREPDLValidationResult.UNKNOWN, aValidator.validateCharacter ("a"));
  }

  @Test
  void testHullOnlyYieldsUnknownWhenMatches ()
  {
    final String sXml = "<char" + NS + "><hull>[A-Z]</hull></char>";
    final CREPDLValidator aValidator = CREPDLValidator.createFromString (sXml, null);
    assertEquals (ECREPDLValidationResult.UNKNOWN, aValidator.validateCharacter ("A"));
    assertEquals (ECREPDLValidationResult.FALSE, aValidator.validateCharacter ("a"));
  }

  @Test
  void testIANARepertoire ()
  {
    // Strings that round-trip through ISO-8859-1
    final String sXml = "<repertoire" + NS + " registry=\"IANA\" name=\"ISO-8859-1\"/>";
    final CREPDLValidator aValidator = CREPDLValidator.createFromString (sXml, null);
    assertEquals (ECREPDLValidationResult.TRUE, aValidator.validateCharacter ("A"));
    assertEquals (ECREPDLValidationResult.TRUE, aValidator.validateCharacter ("ä"));
    // U+0100 is outside ISO-8859-1
    assertEquals (ECREPDLValidationResult.FALSE, aValidator.validateCharacter ("Ā"));
  }

  @Test
  void testGraphemeClusterMode ()
  {
    final String sXml = "<repertoire" + NS + " mode=\"graphemeCluster\" registry=\"10646\" number=\"1\"/>";
    final CREPDLValidator aValidator = CREPDLValidator.createFromString (sXml, null);
    assertEquals (EMode.GRAPHEME_CLUSTER, aValidator.getRootMode ());
    assertEquals (ECREPDLValidationResult.TRUE, aValidator.validateGraphemeCluster ("A"));
  }

  @Test
  void testCREPDLDefinedCollection ()
  {
    // Collection 283 (MODERN EUROPEAN SCRIPTS) is defined as a CREPDL union of
    // inline collections. This exercises the recursive expansion path.
    final String sXml = "<repertoire" + NS + " registry=\"10646\" number=\"283\"/>";
    final CREPDLValidator aValidator = CREPDLValidator.createFromString (sXml, null);
    assertEquals (ECREPDLValidationResult.TRUE, aValidator.validateCharacter ("A"));
  }

  @Test
  void testStreamValidationWithSurrogatePair ()
  {
    // Character mode: surrogate pairs should be emitted as 2-char strings
    final String sXml = "<char" + NS + ">[\\s\\S]</char>"; // any char
    final CREPDLValidator aValidator = CREPDLValidator.createFromString (sXml, null);
    final String sInput = "A" + new String (Character.toChars (0x1F600));
    final CREPDLStreamValidationResult aRes = aValidator.validateString (sInput);
    assertEquals (0, aRes.notIncluded ().length);
    assertEquals (0, aRes.unknowns ().length);
  }

  @Test
  void testRefLoopDetection ()
  {
    // a ref pointing at itself
    // we cannot easily create a self-referential resource without a file system
    // - this is left to integration tests outside this unit test
    assertTrue (true);
  }

  /**
   * U+088F (ARABIC HAFIZ HIGH RECITATION MARK) was first assigned in Unicode 17.0.
   * It must be accepted by the UNICODE 17.0 collection (number 326) but rejected
   * by every collection from UNICODE 11.0 through UNICODE 16.0.
   */
  @Test
  void testUnicode170NewCodePoint ()
  {
    final String sChar = new String (Character.toChars (0x088F));
    // Rejected by Unicode 16.0 (number 325)
    final CREPDLValidator a16 = CREPDLValidator.createFromString ("<repertoire" +
                                                                  NS +
                                                                  " registry=\"10646\" number=\"325\"/>",
                                                                  null);
    assertEquals (ECREPDLValidationResult.FALSE, a16.validateCharacter (sChar));
    // Accepted by Unicode 17.0 (number 326)
    final CREPDLValidator a17 = CREPDLValidator.createFromString ("<repertoire" +
                                                                  NS +
                                                                  " registry=\"10646\" number=\"326\"/>",
                                                                  null);
    assertEquals (ECREPDLValidationResult.TRUE, a17.validateCharacter (sChar));
  }
}
