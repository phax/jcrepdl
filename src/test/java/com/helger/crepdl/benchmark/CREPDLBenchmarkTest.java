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
package com.helger.crepdl.benchmark;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

final class CREPDLBenchmarkTest
{
  @Test
  void testBenchmarkRunsWithDefaultInput ()
  {
    final ByteArrayOutputStream aBuf = new ByteArrayOutputStream ();
    try (final PrintStream aPS = new PrintStream (aBuf, true, StandardCharsets.UTF_8))
    {
      CREPDLBenchmark.run (aPS, CREPDLBenchmark.DEFAULT_INPUT);
    }
    final String sReport = aBuf.toString (StandardCharsets.UTF_8);
    assertNotNull (sReport);
    assertTrue (sReport.contains ("CREPDL Benchmark"), "missing report header");
    assertTrue (sReport.contains ("Lookups"), "missing lookups section");
    assertTrue (sReport.contains ("Verifications"), "missing verifications section");
    assertTrue (sReport.contains ("Coverage assessment"), "missing coverage section");
    assertTrue (sReport.contains ("Recommendation"), "missing recommendation section");
  }

  @Test
  void testAsciiOnlyInputRecommendsBasicLatin ()
  {
    final ByteArrayOutputStream aBuf = new ByteArrayOutputStream ();
    try (final PrintStream aPS = new PrintStream (aBuf, true, StandardCharsets.UTF_8))
    {
      CREPDLBenchmark.run (aPS, "Hello, World!");
    }
    final String sReport = aBuf.toString (StandardCharsets.UTF_8);
    assertTrue (sReport.contains ("BASIC LATIN"), "BASIC LATIN should appear in the report");
    // The "Smallest block scale" recommendation line must mention BASIC LATIN
    // for a plain-ASCII input.
    final int nIdx = sReport.indexOf ("Smallest block scale:");
    assertTrue (nIdx >= 0, "block-scale recommendation missing");
    final int nEol = sReport.indexOf ('\n', nIdx);
    final String sLine = sReport.substring (nIdx, nEol < 0 ? sReport.length () : nEol);
    assertTrue (sLine.contains ("BASIC LATIN"), "Expected BASIC LATIN recommendation, got: " + sLine);
  }

  @Test
  void testCJKInputRequiresBMPOrLarger ()
  {
    final ByteArrayOutputStream aBuf = new ByteArrayOutputStream ();
    try (final PrintStream aPS = new PrintStream (aBuf, true, StandardCharsets.UTF_8))
    {
      CREPDLBenchmark.run (aPS, "世界");
    }
    final String sReport = aBuf.toString (StandardCharsets.UTF_8);
    final int nIdx = sReport.indexOf ("Smallest block scale:");
    assertTrue (nIdx >= 0);
    final int nEol = sReport.indexOf ('\n', nIdx);
    final String sLine = sReport.substring (nIdx, nEol < 0 ? sReport.length () : nEol);
    // BMP or any larger combination must cover U+4E16 / U+754C; BASIC LATIN
    // and ISO-8859-1 must NOT be the recommended minimum.
    assertTrue (sLine.contains ("BMP") || sLine.contains ("UCS"),
                "Expected BMP-or-larger recommendation, got: " + sLine);
  }

  @Test
  void testEmojiInputRequiresSMP ()
  {
    final ByteArrayOutputStream aBuf = new ByteArrayOutputStream ();
    try (final PrintStream aPS = new PrintStream (aBuf, true, StandardCharsets.UTF_8))
    {
      CREPDLBenchmark.run (aPS, "🌍");
    }
    final String sReport = aBuf.toString (StandardCharsets.UTF_8);
    final int nIdx = sReport.indexOf ("Smallest block scale:");
    assertTrue (nIdx >= 0);
    final int nEol = sReport.indexOf ('\n', nIdx);
    final String sLine = sReport.substring (nIdx, nEol < 0 ? sReport.length () : nEol);
    // U+1F30D is in SMP, BMP alone must not be sufficient
    assertTrue (sLine.contains ("SMP") || sLine.contains ("UCS"),
                "Expected SMP-or-larger recommendation, got: " + sLine);
  }
}
