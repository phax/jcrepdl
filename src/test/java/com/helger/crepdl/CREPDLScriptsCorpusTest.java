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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.crepdl.parse.CREPDLParseException;
import com.helger.crepdl.parse.CREPDLReader;
import com.helger.crepdl.validate.CREPDLValidator;

/**
 * Corpus tests that exercise the bundled
 * <a href="https://github.com/CITPCSHARE/CREPDLScripts">CREPDLScripts</a> examples. The examples
 * are copied verbatim (byte-identical) into <code>src/test/resources/CREPDLScripts</code>.
 * <p>
 * Three corpora are exercised:
 * </p>
 * <ul>
 * <li><code>examples/version2/characterMode/*.crepdl</code> &mdash; CREPDL v2, character mode.
 * Should parse and validators should build, with a known set of exceptions documented below.</li>
 * <li><code>examples/version2/graphemeClusterMode/*.crepdl</code> &mdash; CREPDL v2, grapheme
 * cluster mode. Same expectation.</li>
 * <li><code>examples/version1/*.crepdl</code> and
 * <code>examples/cjkvi/cjkvi-tables-master/*.crepdl</code> &mdash; CREPDL v1. Must be rejected with
 * {@link CREPDLParseException} (this library only supports v2).</li>
 * </ul>
 *
 * @author Philip Helger
 */
final class CREPDLScriptsCorpusTest
{
  private static final Logger LOGGER = LoggerFactory.getLogger (CREPDLScriptsCorpusTest.class);

  private static final String CORPUS_ROOT = "/CREPDLScripts/examples";

  /**
   * Files whose XML is malformed in the upstream corpus and so cannot be parsed by a conforming XML
   * processor. They contain a literal <code>mode="character""</code> with a stray double quote.
   */
  private static final Set <String> MALFORMED_XML = Set.of ("bmp.crepdl", "namedSequenceTest.crepdl");

  /**
   * Files that parse OK but cannot be turned into a working validator because they use features
   * intentionally not supported by this library or are deliberate negative test cases in the
   * upstream corpus.
   */
  private static final Set <String> VALIDATOR_BUILD_EXPECTED_FAILURES = Set.of (
                                                                                // <repertoire
                                                                                // registry="IANA"
                                                                                // number="111"/>
                                                                                // &mdash; MIB enum
                                                                                // lookup
                                                                                "8859-15b.crepdl",
                                                                                // self-referential
                                                                                // <ref
                                                                                // href="refLoop.crepdl"/>;
                                                                                // designed to trip
                                                                                // cycle detection
                                                                                "RefLoop.crepdl",
                                                                                // <repertoire
                                                                                // registry="IVD"
                                                                                // name="foo"/>
                                                                                // &mdash; bogus IVD
                                                                                // collection
                                                                                // (upstream
                                                                                // negative test)
                                                                                "IVDfoo.crepdl");

  // ----------------------------------------------------------------------
  // sanity
  // ----------------------------------------------------------------------

  @Test
  void testCorpusRootIsPresent ()
  {
    final Path aRoot = _corpusRoot ();
    assertTrue (Files.isDirectory (aRoot), "Corpus root not found: " + aRoot);
    assertTrue (Files.isDirectory (aRoot.resolve ("version2/characterMode")));
    assertTrue (Files.isDirectory (aRoot.resolve ("version2/graphemeClusterMode")));
    assertTrue (Files.isDirectory (aRoot.resolve ("version1")));
    assertTrue (Files.isDirectory (aRoot.resolve ("cjkvi/cjkvi-tables-master")));
  }

  // ----------------------------------------------------------------------
  // v2 parse-only tests, one per file
  // ----------------------------------------------------------------------

  @ParameterizedTest (name = "v2 characterMode parse: {0}")
  @MethodSource ("v2CharacterModeFiles")
  void testV2CharacterModeParses (final Path aFile)
  {
    final String sName = aFile.getFileName ().toString ();
    if (MALFORMED_XML.contains (sName))
    {
      // Upstream file has invalid XML &mdash; expect a parse exception.
      final CREPDLParseException ex = assertThrows (CREPDLParseException.class,
                                                    () -> CREPDLReader.readScript (aFile.toUri ()));
      assertNotNull (ex.getMessage ());
    }
    else
    {
      // Must be readable
      CREPDLReader.readScript (aFile.toUri ());
    }
  }

  @ParameterizedTest (name = "v2 graphemeClusterMode parse: {0}")
  @MethodSource ("v2GraphemeClusterModeFiles")
  void testV2GraphemeClusterModeParses (final Path aFile)
  {
    CREPDLReader.readScript (aFile.toUri ());
  }

  // ----------------------------------------------------------------------
  // v1 namespace rejection, one per file
  // ----------------------------------------------------------------------

  @ParameterizedTest (name = "v1 (rejected): {0}")
  @MethodSource ("v1Files")
  void testV1FilesRejectedByNamespaceCheck (final Path aFile)
  {
    final URI aUri = aFile.toUri ();
    try
    {
      CREPDLReader.readScript (aUri);
      fail ("Expected CREPDLParseException for v1 namespace, but parsing succeeded for '" + aFile + "'");
    }
    catch (final CREPDLParseException ex)
    {
      assertNotNull (ex.getMessage ());
    }
  }

  @ParameterizedTest (name = "cjkvi (v1, rejected): {0}")
  @MethodSource ("cjkviFiles")
  void testCjkviFilesRejectedByNamespaceCheck (final Path aFile)
  {
    try
    {
      CREPDLReader.readScript (aFile.toUri ());
      fail ("Expected CREPDLParseException for v1 cjkvi script '" + aFile + "'");
    }
    catch (final CREPDLParseException ex)
    {
      assertNotNull (ex.getMessage ());
    }
  }

  // ----------------------------------------------------------------------
  // aggregate: all v2 scripts should build a working validator
  // ----------------------------------------------------------------------

  /**
   * Build a {@link CREPDLValidator} for every v2 script. Files known to be unsupported (see
   * {@link #MALFORMED_XML} and {@link #VALIDATOR_BUILD_EXPECTED_FAILURES}) are reported but not
   * failed. Any other failure fails the test.
   */
  @Test
  void testAllV2ScriptsBuildValidators ()
  {
    final List <Path> aAll = new ArrayList <> ();
    aAll.addAll (_collect (_corpusRoot ().resolve ("version2/characterMode")));
    aAll.addAll (_collect (_corpusRoot ().resolve ("version2/graphemeClusterMode")));
    assertFalse (aAll.isEmpty (), "no v2 scripts found");

    if (false)
      aAll.forEach (x -> LOGGER.info (x.toString ()));

    final List <String> aUnexpectedFailures = new ArrayList <> ();
    int nBuilt = 0;
    int nExpectedSkipped = 0;
    for (final Path aFile : aAll)
    {
      final String sName = aFile.getFileName ().toString ();
      if (MALFORMED_XML.contains (sName) || VALIDATOR_BUILD_EXPECTED_FAILURES.contains (sName))
      {
        nExpectedSkipped++;
        continue;
      }

      try
      {
        CREPDLValidator.create (aFile.toUri ());
        nBuilt++;
      }
      catch (final RuntimeException ex)
      {
        aUnexpectedFailures.add (sName + ": " + ex.getClass ().getSimpleName () + ": " + ex.getMessage ());
      }
    }

    if (!aUnexpectedFailures.isEmpty ())
    {
      fail (aUnexpectedFailures.size () +
            " v2 script(s) failed to build a validator unexpectedly:\n  " +
            String.join ("\n  ", aUnexpectedFailures));
    }
    assertTrue (nBuilt > 0);
    LOGGER.info ("[corpus] built '" +
                 nBuilt +
                 "' v2 validators, skipped '" +
                 nExpectedSkipped +
                 "' expected ('" +
                 (nBuilt + nExpectedSkipped) +
                 "' total)");
  }

  // ----------------------------------------------------------------------
  // targeted ref-loop test
  // ----------------------------------------------------------------------

  @Test
  void testRefLoopIsDetected ()
  {
    final Path aRefLoop = _corpusRoot ().resolve ("version2/characterMode/RefLoop.crepdl");
    assertTrue (Files.isRegularFile (aRefLoop), aRefLoop + " missing");
    // The file references "refLoop.crepdl" (lower-case 'r') which on
    // case-sensitive file systems would fail to resolve. On case-insensitive
    // (default macOS APFS) it resolves to itself and the cycle detector trips.
    // Either way, building a validator MUST fail with a parse exception.
    final CREPDLParseException ex = org.junit.jupiter.api.Assertions.assertThrows (CREPDLParseException.class,
                                                                                   () -> CREPDLValidator.create (aRefLoop.toUri ()));
    assertNotNull (ex.getMessage ());
  }

  // ----------------------------------------------------------------------
  // v1 sanity: at least one file must have been found
  // ----------------------------------------------------------------------

  @Test
  void testV1AndCjkviCorpusNonEmpty ()
  {
    assertFalse (_collect (_corpusRoot ().resolve ("version1")).isEmpty ());
    assertFalse (_collect (_corpusRoot ().resolve ("cjkvi/cjkvi-tables-master")).isEmpty ());
  }

  // ----------------------------------------------------------------------
  // method-source providers
  // ----------------------------------------------------------------------

  static Stream <Arguments> v2CharacterModeFiles ()
  {
    return _collect (_corpusRoot ().resolve ("version2/characterMode")).stream ().map (Arguments::of);
  }

  static Stream <Arguments> v2GraphemeClusterModeFiles ()
  {
    return _collect (_corpusRoot ().resolve ("version2/graphemeClusterMode")).stream ().map (Arguments::of);
  }

  static Stream <Arguments> v1Files ()
  {
    return _collect (_corpusRoot ().resolve ("version1")).stream ().map (Arguments::of);
  }

  static Stream <Arguments> cjkviFiles ()
  {
    return _collect (_corpusRoot ().resolve ("cjkvi/cjkvi-tables-master")).stream ().map (Arguments::of);
  }

  // ----------------------------------------------------------------------
  // helpers
  // ----------------------------------------------------------------------

  private static Path _corpusRoot ()
  {
    final URL aURL = CREPDLScriptsCorpusTest.class.getResource (CORPUS_ROOT);
    if (aURL == null)
      throw new IllegalStateException ("Test corpus not on classpath '" + CORPUS_ROOT + "'");

    try
    {
      return new File (aURL.toURI ()).toPath ();
    }
    catch (final URISyntaxException ex)
    {
      throw new IllegalStateException (ex);
    }
  }

  private static List <Path> _collect (final Path aDir)
  {
    if (!Files.isDirectory (aDir))
      return List.of ();

    try (final Stream <Path> aStream = Files.list (aDir))
    {
      return aStream.filter (p -> p.toString ().endsWith (".crepdl"))
                    .sorted (Comparator.comparing (p -> p.getFileName ().toString ()))
                    .toList ();
    }
    catch (final IOException ex)
    {
      throw new IllegalStateException (ex);
    }
  }
}
