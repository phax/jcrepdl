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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.string.StringHelper;
import com.helger.crepdl.CCREPDL;
import com.helger.crepdl.ECREPDLValidationResult;
import com.helger.crepdl.repertoire.ISO10646Collections;
import com.helger.crepdl.validate.CREPDLValidator;

/**
 * Small benchmark for the jcrepdl library. Three sections are run in order:
 * <ol>
 * <li><b>Lookups</b> &mdash; throughput of {@link ISO10646Collections} predefined-collection
 * lookups (inline / out-of-line / CREPDL-script).</li>
 * <li><b>Verifications</b> &mdash; throughput of {@link CREPDLValidator#validateCharacter(String)
 * validateCharacter} and {@link CREPDLValidator#validateString(String) validateString} for a few
 * representative scripts.</li>
 * <li><b>Coverage assessment</b> &mdash; for an arbitrary input text, find the smallest predefined
 * repertoire (block-scale, Unicode-age scale, and IANA-charset scale) that fully covers it, and
 * recommend it.</li>
 * </ol>
 * <p>
 * Run from Maven:
 * </p>
 *
 * <pre>
 * mvn -q compile exec:java \
 *     -Dexec.mainClass=com.helger.crepdl.benchmark.CREPDLBenchmark \
 *     -Dexec.args="Hello, &#x4e16;&#x754c; &#x1f30d;"
 * </pre>
 *
 * @author Philip Helger
 */
public final class CREPDLBenchmark
{
  private static final String NS = " xmlns=\"" + CCREPDL.NAMESPACE_URI_V2 + "\"";

  /** Default input text if none is supplied on the command line. */
  public static final String DEFAULT_INPUT = "Hello, 世界 🌍 — Café déjà";

  private static final int LOOKUP_WARMUP = 10_000;
  private static final int LOOKUP_ITERATIONS = 1_000_000;
  private static final int VERIFY_WARMUP = 1_000;
  private static final int VERIFY_ITERATIONS = 100_000;

  private CREPDLBenchmark ()
  {}

  // ----------------------------------------------------------------------
  // Output helpers
  // ----------------------------------------------------------------------

  private static void _section (@NonNull final PrintStream aOut, @NonNull final String sTitle)
  {
    aOut.println ();
    aOut.println ("=== " + sTitle + " ===");
  }

  /**
   * Format a non-negative double to a fixed number of fractional digits,
   * locale-independently and without going through {@code String.format}.
   * Half-up rounding.
   */
  @NonNull
  private static String _fixed (final double dValue, final int nDecimals)
  {
    long nScale = 1;
    for (int i = 0; i < nDecimals; i++)
      nScale *= 10L;
    final long nScaled = Math.round (dValue * nScale);
    final boolean bNegative = nScaled < 0;
    final long nAbs = Math.abs (nScaled);
    final long nIntPart = nAbs / nScale;
    final long nFracPart = nAbs % nScale;
    return (bNegative ? "-" : "") +
           nIntPart +
           (nDecimals > 0 ? "." + StringHelper.getWithLeading (nFracPart, nDecimals, '0') : "");
  }

  private static void _reportTime (@NonNull final PrintStream aOut,
                                   @NonNull final String sLabel,
                                   final int nIterations,
                                   final long nElapsedNs)
  {
    final double dNsPerOp = (double) nElapsedNs / nIterations;
    final double dOpsPerSec = 1e9 / dNsPerOp;
    aOut.println ("  " +
                  StringHelper.getWithTrailing (sLabel, 44, ' ') +
                  " " +
                  StringHelper.getWithLeading (_fixed (dNsPerOp, 1), 9, ' ') +
                  " ns/op   (" +
                  StringHelper.getWithLeading (_fixed (dOpsPerSec / 1e6, 2), 7, ' ') +
                  " M ops/sec)");
  }

  private static long _timeNs (final int nIterations, @NonNull final Runnable aBody)
  {
    final long nStart = System.nanoTime ();
    for (int i = 0; i < nIterations; i++)
      aBody.run ();
    return System.nanoTime () - nStart;
  }

  // ----------------------------------------------------------------------
  // Coverage helpers
  // ----------------------------------------------------------------------

  /**
   * Pair (name, CREPDL XML) used to enumerate candidate repertoires.
   */
  private record CandidateRepertoire (String name, String crepdlXml)
  {
    /* nothing else */
  }

  @NonNull
  private static String _wrapISO10646ByNumber (final int nNumber)
  {
    return "<repertoire" + NS + " registry=\"10646\" number=\"" + nNumber + "\"/>";
  }

  @NonNull
  private static String _wrapISO10646ByName (@NonNull final String sName)
  {
    return "<repertoire" + NS + " registry=\"10646\" name=\"" + sName + "\"/>";
  }

  @NonNull
  private static String _wrapIANA (@NonNull final String sCharset)
  {
    return "<repertoire" + NS + " registry=\"IANA\" name=\"" + sCharset + "\"/>";
  }

  @NonNull
  private static String _wrapUnionBlocks (final int... aNumbers)
  {
    final StringBuilder aSB = new StringBuilder ("<union" + NS + ">");
    for (final int n : aNumbers)
      aSB.append ("<repertoire registry=\"10646\" number=\"").append (n).append ("\"/>");
    aSB.append ("</union>");
    return aSB.toString ();
  }

  /**
   * Iterate the characters of {@code sInput} in CHARACTER mode (one Unicode codepoint per unit,
   * surrogate pairs collapsed).
   */
  @NonNull
  private static List <String> _codepointUnits (@NonNull final String sInput)
  {
    final List <String> aRet = new ArrayList <> ();
    int i = 0;
    while (i < sInput.length ())
    {
      final int nCp = sInput.codePointAt (i);
      aRet.add (new String (Character.toChars (nCp)));
      i += Character.charCount (nCp);
    }
    return aRet;
  }

  /**
   * Try one candidate repertoire against the supplied input. Returns the number of code-point units
   * that were NOT included in the repertoire. A return value of zero means the repertoire fully
   * covers the input.
   */
  private static int _countUncovered (@NonNull final CREPDLValidator aValidator, @NonNull final List <String> aUnits)
  {
    int nUncovered = 0;
    for (final String sUnit : aUnits)
    {
      final ECREPDLValidationResult eRes = aValidator.validateCharacter (sUnit);
      if (eRes != ECREPDLValidationResult.TRUE)
        nUncovered++;
    }
    return nUncovered;
  }

  @NonNull
  private static Map <String, CandidateRepertoire> _buildBlockScale ()
  {
    final Map <String, CandidateRepertoire> aRet = new LinkedHashMap <> ();
    aRet.put ("BASIC LATIN (U+0020..U+007E)", new CandidateRepertoire ("BASIC LATIN", _wrapISO10646ByNumber (1)));
    aRet.put ("ISO-8859-1 (Basic Latin + Latin-1)", new CandidateRepertoire ("LATIN-1", _wrapUnionBlocks (1, 2)));
    aRet.put ("BMP (Plane 0)", new CandidateRepertoire ("BMP", _wrapISO10646ByNumber (300)));
    aRet.put ("BMP + SMP (Planes 0-1)", new CandidateRepertoire ("BMP+SMP", _wrapUnionBlocks (300, 1000)));
    aRet.put ("BMP + SMP + SIP (Planes 0-2)",
              new CandidateRepertoire ("BMP+SMP+SIP", _wrapUnionBlocks (300, 1000, 2000)));
    aRet.put ("Full UCS (Planes 0-2 + SSP)",
              new CandidateRepertoire ("Full UCS", _wrapUnionBlocks (300, 1000, 2000, 3000)));
    return aRet;
  }

  @NonNull
  private static Map <String, CandidateRepertoire> _buildUnicodeAgeScale ()
  {
    final Map <String, CandidateRepertoire> aRet = new LinkedHashMap <> ();
    // Numbers 303..318 are predefined as "UNICODE x.y" CREPDL-defined collections
    aRet.put ("Unicode 3.1", new CandidateRepertoire ("Unicode 3.1", _wrapISO10646ByNumber (303)));
    aRet.put ("Unicode 3.2", new CandidateRepertoire ("Unicode 3.2", _wrapISO10646ByNumber (304)));
    aRet.put ("Unicode 4.0", new CandidateRepertoire ("Unicode 4.0", _wrapISO10646ByNumber (305)));
    aRet.put ("Unicode 4.1", new CandidateRepertoire ("Unicode 4.1", _wrapISO10646ByNumber (306)));
    aRet.put ("Unicode 5.0", new CandidateRepertoire ("Unicode 5.0", _wrapISO10646ByNumber (307)));
    aRet.put ("Unicode 5.1", new CandidateRepertoire ("Unicode 5.1", _wrapISO10646ByNumber (308)));
    aRet.put ("Unicode 5.2", new CandidateRepertoire ("Unicode 5.2", _wrapISO10646ByNumber (309)));
    aRet.put ("Unicode 6.0", new CandidateRepertoire ("Unicode 6.0", _wrapISO10646ByNumber (310)));
    aRet.put ("Unicode 6.1", new CandidateRepertoire ("Unicode 6.1", _wrapISO10646ByNumber (311)));
    aRet.put ("Unicode 6.2", new CandidateRepertoire ("Unicode 6.2", _wrapISO10646ByNumber (312)));
    aRet.put ("Unicode 6.3", new CandidateRepertoire ("Unicode 6.3", _wrapISO10646ByNumber (313)));
    aRet.put ("Unicode 7.0", new CandidateRepertoire ("Unicode 7.0", _wrapISO10646ByNumber (314)));
    aRet.put ("Unicode 8.0", new CandidateRepertoire ("Unicode 8.0", _wrapISO10646ByNumber (315)));
    aRet.put ("Unicode 9.0", new CandidateRepertoire ("Unicode 9.0", _wrapISO10646ByNumber (316)));
    aRet.put ("Unicode 10.0", new CandidateRepertoire ("Unicode 10.0", _wrapISO10646ByNumber (317)));
    aRet.put ("Unicode 11.0", new CandidateRepertoire ("Unicode 11.0", _wrapISO10646ByNumber (318)));
    aRet.put ("Unicode 12.0", new CandidateRepertoire ("Unicode 12.0", _wrapISO10646ByNumber (319)));
    aRet.put ("Unicode 12.1", new CandidateRepertoire ("Unicode 12.1", _wrapISO10646ByNumber (320)));
    aRet.put ("Unicode 13.0", new CandidateRepertoire ("Unicode 13.0", _wrapISO10646ByNumber (321)));
    aRet.put ("Unicode 14.0", new CandidateRepertoire ("Unicode 14.0", _wrapISO10646ByNumber (322)));
    aRet.put ("Unicode 15.0", new CandidateRepertoire ("Unicode 15.0", _wrapISO10646ByNumber (323)));
    aRet.put ("Unicode 15.1", new CandidateRepertoire ("Unicode 15.1", _wrapISO10646ByNumber (324)));
    aRet.put ("Unicode 16.0", new CandidateRepertoire ("Unicode 16.0", _wrapISO10646ByNumber (325)));
    aRet.put ("Unicode 17.0", new CandidateRepertoire ("Unicode 17.0", _wrapISO10646ByNumber (326)));
    return aRet;
  }

  @NonNull
  private static Map <String, CandidateRepertoire> _buildIANAScale ()
  {
    final Map <String, CandidateRepertoire> aRet = new LinkedHashMap <> ();
    aRet.put ("US-ASCII", new CandidateRepertoire ("US-ASCII", _wrapIANA ("US-ASCII")));
    aRet.put ("ISO-8859-1", new CandidateRepertoire ("ISO-8859-1", _wrapIANA ("ISO-8859-1")));
    aRet.put ("Shift_JIS", new CandidateRepertoire ("Shift_JIS", _wrapIANA ("Shift_JIS")));
    aRet.put ("UTF-8", new CandidateRepertoire ("UTF-8", _wrapIANA ("UTF-8")));
    return aRet;
  }

  @Nullable
  private static String _firstCoveringName (@NonNull final Map <String, CandidateRepertoire> aScale,
                                            @NonNull final List <String> aUnits)
  {
    for (final Map.Entry <String, CandidateRepertoire> aE : aScale.entrySet ())
    {
      final CREPDLValidator aValidator = CREPDLValidator.createFromString (aE.getValue ().crepdlXml (), null);
      if (_countUncovered (aValidator, aUnits) == 0)
        return aE.getKey ();
    }
    return null;
  }

  private static void _runScale (@NonNull final PrintStream aOut,
                                 @NonNull final String sScaleTitle,
                                 @NonNull final Map <String, CandidateRepertoire> aScale,
                                 @NonNull final List <String> aUnits)
  {
    aOut.println ();
    aOut.println ("  " + sScaleTitle + ":");
    for (final Map.Entry <String, CandidateRepertoire> aE : aScale.entrySet ())
    {
      try
      {
        final CREPDLValidator aValidator = CREPDLValidator.createFromString (aE.getValue ().crepdlXml (), null);
        final int nUncovered = _countUncovered (aValidator, aUnits);
        final String sStatus = nUncovered == 0 ? "COVERED" : "not covered (" + nUncovered + " char(s) outside)";
        aOut.println ("    " + StringHelper.getWithTrailing (aE.getKey (), 36, ' ') + " : " + sStatus);
      }
      catch (final RuntimeException ex)
      {
        aOut.println ("    " +
                      StringHelper.getWithTrailing (aE.getKey (), 36, ' ') +
                      " : ERROR (" +
                      ex.getMessage () +
                      ")");
      }
    }
  }

  // ----------------------------------------------------------------------
  // Section bodies (each depends on the helpers above)
  // ----------------------------------------------------------------------

  private static void _benchmarkLookups (@NonNull final PrintStream aOut)
  {
    // Warmup
    for (int i = 0; i < LOOKUP_WARMUP; i++)
    {
      ISO10646Collections.lookupInlineByName ("BASIC LATIN");
      ISO10646Collections.lookupInlineByNumber (1);
      ISO10646Collections.lookupOutOfLineResource ("IICORE");
      ISO10646Collections.lookupCREPDLScriptResource (303);
    }

    long nT = _timeNs (LOOKUP_ITERATIONS, () -> ISO10646Collections.lookupInlineByName ("BASIC LATIN"));
    _reportTime (aOut, "Lookup inline by name", LOOKUP_ITERATIONS, nT);

    nT = _timeNs (LOOKUP_ITERATIONS, () -> ISO10646Collections.lookupInlineByNumber (1));
    _reportTime (aOut, "Lookup inline by number", LOOKUP_ITERATIONS, nT);

    nT = _timeNs (LOOKUP_ITERATIONS, () -> ISO10646Collections.lookupOutOfLineResource ("IICORE"));
    _reportTime (aOut, "Lookup out-of-line by name", LOOKUP_ITERATIONS, nT);

    nT = _timeNs (LOOKUP_ITERATIONS, () -> ISO10646Collections.lookupOutOfLineResource (370));
    _reportTime (aOut, "Lookup out-of-line by number", LOOKUP_ITERATIONS, nT);

    nT = _timeNs (LOOKUP_ITERATIONS, () -> ISO10646Collections.lookupCREPDLScriptResource (303));
    _reportTime (aOut, "Lookup CREPDL-script by number", LOOKUP_ITERATIONS, nT);
  }

  private static void _benchmarkVerifications (@NonNull final PrintStream aOut)
  {
    // Three representative scripts: BASIC LATIN (inline, small),
    // BMP (inline but large), and Unicode 8.0 (CREPDL-script / expanded union).
    final CREPDLValidator aBasicLatin = CREPDLValidator.createFromString (_wrapISO10646ByNumber (1), null);
    final CREPDLValidator aBMP = CREPDLValidator.createFromString (_wrapISO10646ByNumber (300), null);
    final CREPDLValidator aUcs8 = CREPDLValidator.createFromString (_wrapISO10646ByNumber (315), null);

    // Warmup
    for (int i = 0; i < VERIFY_WARMUP; i++)
    {
      aBasicLatin.validateCharacter ("A");
      aBMP.validateCharacter ("A");
      aUcs8.validateCharacter ("A");
    }

    long nT = _timeNs (VERIFY_ITERATIONS, () -> aBasicLatin.validateCharacter ("A"));
    _reportTime (aOut, "validateCharacter (BASIC LATIN, cached)", VERIFY_ITERATIONS, nT);

    nT = _timeNs (VERIFY_ITERATIONS, () -> aBMP.validateCharacter ("世"));
    _reportTime (aOut, "validateCharacter (BMP, cached)", VERIFY_ITERATIONS, nT);

    nT = _timeNs (VERIFY_ITERATIONS, () -> aUcs8.validateCharacter ("世"));
    _reportTime (aOut, "validateCharacter (Unicode 8.0, cached)", VERIFY_ITERATIONS, nT);

    // Whole-string validation. The cache hits per character but the string
    // enumerator runs end-to-end every iteration.
    final String sShort = "Hello, World!";
    final String sLong;
    {
      final StringBuilder aSB = new StringBuilder ();
      for (int i = 0; i < 80; i++)
        aSB.append ("The quick brown fox jumps over the lazy dog. ");
      sLong = aSB.toString ();
    }

    nT = _timeNs (VERIFY_ITERATIONS / 10, () -> aBasicLatin.validateString (sShort));
    _reportTime (aOut, "validateString (BASIC LATIN, 13 chars)", VERIFY_ITERATIONS / 10, nT);
    aOut.println ("    -> " + _fixed (sShort.length () * (VERIFY_ITERATIONS / 10) * 1e3 / nT, 2) + " M chars/sec");

    nT = _timeNs (VERIFY_ITERATIONS / 100, () -> aBasicLatin.validateString (sLong));
    _reportTime (aOut, "validateString (BASIC LATIN, " + sLong.length () + " chars)", VERIFY_ITERATIONS / 100, nT);
    aOut.println ("    -> " + _fixed (sLong.length () * (VERIFY_ITERATIONS / 100) * 1e3 / nT, 2) + " M chars/sec");
  }

  private static void _assessCoverage (@NonNull final PrintStream aOut, @NonNull final String sInput)
  {
    final List <String> aUnits = _codepointUnits (sInput);

    aOut.println ();
    aOut.println ("  Input: \"" + sInput + "\"");
    aOut.println ("  Code-point count: " + aUnits.size () + ", UTF-16 length: " + sInput.length ());

    final Map <String, CandidateRepertoire> aBlocks = _buildBlockScale ();
    final Map <String, CandidateRepertoire> aAges = _buildUnicodeAgeScale ();
    final Map <String, CandidateRepertoire> aIana = _buildIANAScale ();

    _runScale (aOut, "Block / plane scale", aBlocks, aUnits);
    _runScale (aOut, "Unicode age scale", aAges, aUnits);
    _runScale (aOut, "IANA charset scale (round-trip)", aIana, aUnits);

    aOut.println ();
    aOut.println ("  Recommendation:");
    aOut.println ("    Smallest block scale:        " + _orNone (_firstCoveringName (aBlocks, aUnits)));
    aOut.println ("    Smallest Unicode age:        " + _orNone (_firstCoveringName (aAges, aUnits)));
    aOut.println ("    Smallest IANA charset:       " + _orNone (_firstCoveringName (aIana, aUnits)));
  }

  @NonNull
  private static String _orNone (@Nullable final String s)
  {
    return s == null ? "(none of the candidates fully covers the input)" : s;
  }

  // ----------------------------------------------------------------------
  // Public API
  // ----------------------------------------------------------------------

  /**
   * Run the full benchmark on the given input text, writing report lines to the given stream.
   *
   * @param aOut
   *        Destination for the report. Never <code>null</code>.
   * @param sInput
   *        Arbitrary input text. Never <code>null</code>.
   */
  public static void run (@NonNull final PrintStream aOut, @NonNull final String sInput)
  {
    _section (aOut, "CREPDL Benchmark");
    _section (aOut, "[1] Lookups");
    _benchmarkLookups (aOut);

    _section (aOut, "[2] Verifications");
    _benchmarkVerifications (aOut);

    _section (aOut, "[3] Coverage assessment for arbitrary text");
    _assessCoverage (aOut, sInput);
  }

  /**
   * Command-line entry point. Pass an arbitrary text as the first argument; if none is given,
   * {@link #DEFAULT_INPUT} is used.
   *
   * @param aArgs
   *        Optional command-line arguments.
   */
  public static void main (final String [] aArgs)
  {
    final String sInput = aArgs.length > 0 ? aArgs[0] : DEFAULT_INPUT;
    run (System.out, sInput);
  }
}
