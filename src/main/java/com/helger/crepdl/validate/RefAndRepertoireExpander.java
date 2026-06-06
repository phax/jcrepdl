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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.crepdl.model.CREPDLDifference;
import com.helger.crepdl.model.CREPDLIntersection;
import com.helger.crepdl.model.CREPDLRef;
import com.helger.crepdl.model.CREPDLRepertoire;
import com.helger.crepdl.model.CREPDLUnion;
import com.helger.crepdl.model.ICREPDLNode;
import com.helger.crepdl.model.RegistryISO10646;
import com.helger.crepdl.parse.CREPDLParseException;
import com.helger.crepdl.parse.CREPDLReader;
import com.helger.crepdl.repertoire.ISO10646Collections;

/**
 * Pre-processes a CREPDL tree so subsequent validation only has to walk concrete subtrees. Two
 * transformations are applied:
 * <ol>
 * <li><code>&lt;ref href="..."/&gt;</code> is replaced by a {@link CREPDLRef} whose single child is
 * the recursively-expanded referenced script. Cycles raise a {@link CREPDLParseException}.</li>
 * <li><code>&lt;repertoire registry="10646"&gt;</code> elements that name a collection defined
 * recursively (a "CREPDL-script" collection) are replaced by their expansion.</li>
 * </ol>
 *
 * @author Philip Helger
 */
public final class RefAndRepertoireExpander
{
  // Hard ceiling on combined nesting + ref-follow + ISO-10646-collection
  // expansion depth, to bound recursion in _expandRecursive on pathological
  // input. Counted as the depth of the produced tree: structural children,
  // ref-follow expansions, and CREPDL-script collection expansions each
  // contribute one level.
  private static final int MAX_EXPANSION_DEPTH = 100;

  private RefAndRepertoireExpander ()
  {}

  // ----------------------------------------------------------------------
  // Private helpers, leaf-first
  // ----------------------------------------------------------------------

  @Nullable
  private static String _findCREPDLScript (@NonNull final RegistryISO10646 aIso)
  {
    if (aIso.name () != null)
    {
      final String s = ISO10646Collections.lookupCREPDLScriptResource (aIso.name ());
      if (s != null)
        return s;
    }
    if (aIso.number () != null)
      return ISO10646Collections.lookupCREPDLScriptResource (aIso.number ().intValue ());
    return null;
  }

  @NonNull
  private static List <ICREPDLNode> _expandChildren (@NonNull final List <ICREPDLNode> aChildren,
                                                     @NonNull final Set <URI> aRefParents,
                                                     @NonNull final Set <RegistryISO10646> aIsoParents,
                                                     @NonNull final ICREPDLRefResolver aResolver,
                                                     final int nDepth)
  {
    final List <ICREPDLNode> aRet = new ArrayList <> (aChildren.size ());
    for (final ICREPDLNode aChild : aChildren)
      aRet.add (_expandRecursive (aChild, aRefParents, aIsoParents, aResolver, nDepth));
    return aRet;
  }

  @NonNull
  private static ICREPDLNode _expandRecursive (@NonNull final ICREPDLNode aNode,
                                               @NonNull final Set <URI> aRefParents,
                                               @NonNull final Set <RegistryISO10646> aIsoParents,
                                               @NonNull final ICREPDLRefResolver aResolver,
                                               final int nDepth)
  {
    if (nDepth > MAX_EXPANSION_DEPTH)
      throw new CREPDLParseException ("Maximum CREPDL expansion depth of " + MAX_EXPANSION_DEPTH + " exceeded");

    if (aNode instanceof final CREPDLUnion aU)
      return new CREPDLUnion (aU.mode (),
                              aU.minUcsVersion (),
                              aU.maxUcsVersion (),
                              _expandChildren (aU.children (), aRefParents, aIsoParents, aResolver, nDepth + 1));

    if (aNode instanceof final CREPDLIntersection aI)
      return new CREPDLIntersection (aI.mode (),
                                     aI.minUcsVersion (),
                                     aI.maxUcsVersion (),
                                     _expandChildren (aI.children (), aRefParents, aIsoParents, aResolver, nDepth + 1));

    if (aNode instanceof final CREPDLDifference aD)
      return new CREPDLDifference (aD.mode (),
                                   aD.minUcsVersion (),
                                   aD.maxUcsVersion (),
                                   _expandChildren (aD.children (), aRefParents, aIsoParents, aResolver, nDepth + 1));

    if (aNode instanceof final CREPDLRef aR)
    {
      // Cycle detection runs before the resolver is consulted, so a
      // cyclic ref is reported as such instead of bouncing off the
      // resolver as "unknown" or being silently looped.
      if (aRefParents.contains (aR.href ()))
        throw new CREPDLParseException ("Loop caused by <ref> elements at '" + aR.href () + "'");
      final ICREPDLNode aReferenced;
      try (final CREPDLRefSource aSource = aResolver.resolve (aR.href ()))
      {
        aReferenced = CREPDLReader.readScript (aSource.stream (), aSource.baseUri ());
      }
      catch (final IOException ex)
      {
        // CREPDLReader.readScript wraps its own IO errors as
        // CREPDLParseException, so this catch only fires when the
        // resolver-owned stream fails to close cleanly.
        throw new CREPDLParseException ("Failed to close ref source for '" + aR.href () + "'", ex);
      }
      final Set <URI> aChain = new HashSet <> (aRefParents);
      aChain.add (aR.href ());
      final ICREPDLNode aExpanded = _expandRecursive (aReferenced, aChain, aIsoParents, aResolver, nDepth + 1);
      return new CREPDLRef (aR.mode (), aR.minUcsVersion (), aR.maxUcsVersion (), aR.href (), List.of (aExpanded));
    }

    if (aNode instanceof final CREPDLRepertoire aRep && aRep.registry () instanceof final RegistryISO10646 aIso)
    {
      final String sResource = _findCREPDLScript (aIso);
      if (sResource != null)
      {
        if (aIsoParents.contains (aIso))
        {
          // Self-reference inside a CREPDL-defined ISO 10646 collection.
          // Stop the recursion by yielding the union identity (empty union =
          // FALSE for every input), which is correct: union(X, Y, X) == union(X, Y).
          return new CREPDLUnion (aRep.mode (), aRep.minUcsVersion (), aRep.maxUcsVersion (), List.of ());
        }
        // The bundled CREPDL-script collections are loaded directly from
        // the classpath, bypassing the user-supplied resolver. They are
        // trusted shipped data.
        final ICREPDLNode aSub;
        try (final InputStream aIS = RefAndRepertoireExpander.class.getResourceAsStream (sResource))
        {
          if (aIS == null)
            throw new CREPDLParseException ("Missing bundled resource '" + sResource + "'");
          aSub = CREPDLReader.readScript (aIS, null);
        }
        catch (final IOException ex)
        {
          throw new CREPDLParseException ("Failed to read bundled resource '" + sResource + "'", ex);
        }
        final Set <RegistryISO10646> aChain = new HashSet <> (aIsoParents);
        aChain.add (aIso);
        return _expandRecursive (aSub, aRefParents, aChain, aResolver, nDepth + 1);
      }
    }
    return aNode;
  }

  /**
   * Expand the given root tree using the default {@link DenyAllRefResolver}. Any
   * <code>&lt;ref&gt;</code> in the input raises a {@link CREPDLParseException}. Use
   * {@link #expand(ICREPDLNode, ICREPDLRefResolver)} to allow refs through a specific resolver.
   *
   * @param aRoot
   *        Original tree. Never <code>null</code>.
   * @return Expanded tree. Never <code>null</code>.
   */
  @NonNull
  public static ICREPDLNode expand (@NonNull final ICREPDLNode aRoot)
  {
    return expand (aRoot, DenyAllRefResolver.INSTANCE);
  }

  /**
   * Expand the given root tree, routing every <code>&lt;ref&gt;</code> URI through the given
   * resolver.
   *
   * @param aRoot
   *        Original tree. Never <code>null</code>.
   * @param aResolver
   *        Ref resolver. Never <code>null</code>.
   * @return Expanded tree. Never <code>null</code>.
   */
  @NonNull
  public static ICREPDLNode expand (@NonNull final ICREPDLNode aRoot, @NonNull final ICREPDLRefResolver aResolver)
  {
    return _expandRecursive (aRoot, new HashSet <> (), new HashSet <> (), aResolver, 0);
  }
}
