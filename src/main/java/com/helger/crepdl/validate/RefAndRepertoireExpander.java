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
                                                     final int nDepth)
  {
    final List <ICREPDLNode> aRet = new ArrayList <> (aChildren.size ());
    for (final ICREPDLNode aChild : aChildren)
      aRet.add (_expandRecursive (aChild, aRefParents, aIsoParents, nDepth));
    return aRet;
  }

  @NonNull
  private static ICREPDLNode _expandRecursive (@NonNull final ICREPDLNode aNode,
                                               @NonNull final Set <URI> aRefParents,
                                               @NonNull final Set <RegistryISO10646> aIsoParents,
                                               final int nDepth)
  {
    if (nDepth > MAX_EXPANSION_DEPTH)
      throw new CREPDLParseException ("Maximum CREPDL expansion depth of " + MAX_EXPANSION_DEPTH + " exceeded");

    if (aNode instanceof final CREPDLUnion aU)
      return new CREPDLUnion (aU.mode (),
                              aU.minUcsVersion (),
                              aU.maxUcsVersion (),
                              _expandChildren (aU.children (), aRefParents, aIsoParents, nDepth + 1));
    if (aNode instanceof final CREPDLIntersection aI)
      return new CREPDLIntersection (aI.mode (),
                                     aI.minUcsVersion (),
                                     aI.maxUcsVersion (),
                                     _expandChildren (aI.children (), aRefParents, aIsoParents, nDepth + 1));
    if (aNode instanceof final CREPDLDifference aD)
      return new CREPDLDifference (aD.mode (),
                                   aD.minUcsVersion (),
                                   aD.maxUcsVersion (),
                                   _expandChildren (aD.children (), aRefParents, aIsoParents, nDepth + 1));
    if (aNode instanceof final CREPDLRef aR)
    {
      if (aRefParents.contains (aR.href ()))
        throw new CREPDLParseException ("Loop caused by <ref> elements at '" + aR.href () + "'");
      final ICREPDLNode aReferenced = CREPDLReader.readScript (aR.href ());
      final Set <URI> aChain = new HashSet <> (aRefParents);
      aChain.add (aR.href ());
      final ICREPDLNode aExpanded = _expandRecursive (aReferenced, aChain, aIsoParents, nDepth + 1);
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
        return _expandRecursive (aSub, aRefParents, aChain, nDepth + 1);
      }
    }
    return aNode;
  }

  /**
   * Expand the given root tree.
   *
   * @param aRoot
   *        Original tree. Never <code>null</code>.
   * @return Expanded tree. Never <code>null</code>.
   */
  @NonNull
  public static ICREPDLNode expand (@NonNull final ICREPDLNode aRoot)
  {
    return _expandRecursive (aRoot, new HashSet <> (), new HashSet <> (), 0);
  }
}
