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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.helger.crepdl.parse.CREPDLParseException;

/**
 * Unit tests for the {@code <ref>} resolver SPI.
 *
 * @author Philip Helger
 */
final class RefResolverTest
{
  private static final String NS = "http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0";

  private static final String LEAF_SCRIPT = "<char xmlns=\"" + NS + "\">[A-Z]</char>";

  // ----------------------------------------------------------------------
  // DenyAllRefResolver
  // ----------------------------------------------------------------------

  @Test
  void testDenyAllRejectsEveryUri ()
  {
    final CREPDLParseException ex = assertThrows (CREPDLParseException.class,
                                                  () -> DenyAllRefResolver.INSTANCE.resolve (URI.create ("file:/anywhere")));
    assertTrue (ex.getMessage ().contains ("deny-all"));
  }

  @Test
  void testDenyAllIsTheDefaultForCreateFromString (@TempDir final Path aTmp) throws IOException
  {
    // Top-level script contains a single <ref>; with the default deny-all
    // resolver, building the validator must fail at expansion time.
    final Path aLeaf = aTmp.resolve ("leaf.crepdl");
    Files.writeString (aLeaf, LEAF_SCRIPT);
    final String sTop = "<union xmlns=\"" + NS + "\"><ref href=\"" + aLeaf.toUri () + "\"/></union>";

    final CREPDLParseException ex = assertThrows (CREPDLParseException.class,
                                                  () -> CREPDLValidator.createFromString (sTop, null));
    assertTrue (ex.getMessage ().contains ("deny-all"), ex.getMessage ());
  }

  // ----------------------------------------------------------------------
  // FileSystemRefResolver
  // ----------------------------------------------------------------------

  @Test
  void testFileSystemAcceptsFileInsideRoot (@TempDir final Path aTmp) throws IOException
  {
    final Path aLeaf = aTmp.resolve ("leaf.crepdl");
    Files.writeString (aLeaf, LEAF_SCRIPT);

    final FileSystemRefResolver aResolver = new FileSystemRefResolver (aTmp);
    try (final CREPDLRefSource aSource = aResolver.resolve (aLeaf.toUri ()))
    {
      assertNotNull (aSource.stream ());
      assertEquals (aLeaf.toUri (), aSource.baseUri ());
    }
  }

  @Test
  void testFileSystemRejectsFileOutsideRoot (@TempDir final Path aTmp) throws IOException
  {
    final Path aInside = aTmp.resolve ("inside");
    Files.createDirectory (aInside);
    final Path aOutside = aTmp.resolve ("outside.crepdl");
    Files.writeString (aOutside, LEAF_SCRIPT);

    final FileSystemRefResolver aResolver = new FileSystemRefResolver (aInside);
    final CREPDLParseException ex = assertThrows (CREPDLParseException.class,
                                                  () -> aResolver.resolve (aOutside.toUri ()));
    assertTrue (ex.getMessage ().contains ("outside the allowed root"), ex.getMessage ());
  }

  @Test
  void testFileSystemRejectsTraversalEscape (@TempDir final Path aTmp) throws IOException
  {
    final Path aInside = aTmp.resolve ("inside");
    Files.createDirectory (aInside);
    final Path aOutside = aTmp.resolve ("outside.crepdl");
    Files.writeString (aOutside, LEAF_SCRIPT);

    // Build a URI of the form file:/.../inside/../outside.crepdl that
    // normalises to a path outside the resolver root.
    final URI aTraversal = aInside.resolve ("..").resolve ("outside.crepdl").toUri ();

    final FileSystemRefResolver aResolver = new FileSystemRefResolver (aInside);
    final CREPDLParseException ex = assertThrows (CREPDLParseException.class, () -> aResolver.resolve (aTraversal));
    assertTrue (ex.getMessage ().contains ("outside the allowed root"), ex.getMessage ());
  }

  @Test
  void testFileSystemRejectsNonFileScheme (@TempDir final Path aTmp)
  {
    final FileSystemRefResolver aResolver = new FileSystemRefResolver (aTmp);
    final CREPDLParseException ex = assertThrows (CREPDLParseException.class,
                                                  () -> aResolver.resolve (URI.create ("https://example.invalid/x")));
    assertTrue (ex.getMessage ().contains ("scheme must be 'file'"), ex.getMessage ());
  }

  // ----------------------------------------------------------------------
  // End-to-end through the validator
  // ----------------------------------------------------------------------

  @Test
  void testValidatorBuildsWhenResolverAllowsRef (@TempDir final Path aTmp) throws IOException
  {
    final Path aLeaf = aTmp.resolve ("leaf.crepdl");
    Files.writeString (aLeaf, LEAF_SCRIPT);
    final String sTop = "<union xmlns=\"" + NS + "\"><ref href=\"" + aLeaf.toUri () + "\"/></union>";

    final CREPDLValidator aValidator = CREPDLValidator.createFromString (sTop, null, new FileSystemRefResolver (aTmp));
    assertSame (com.helger.crepdl.ECREPDLValidationResult.TRUE, aValidator.validateCharacter ("A"));
  }
}
