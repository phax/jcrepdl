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

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.crepdl.EThreeValuedBoolean;
import com.helger.crepdl.model.CREPDLChar;
import com.helger.crepdl.model.CREPDLDifference;
import com.helger.crepdl.model.CREPDLIntersection;
import com.helger.crepdl.model.CREPDLRef;
import com.helger.crepdl.model.CREPDLRepertoire;
import com.helger.crepdl.model.CREPDLUnion;
import com.helger.crepdl.model.ICREPDLNode;

/**
 * Three-valued evaluator for the expanded CREPDL tree. Mirrors
 * <code>StringValidation.fs</code> from the reference implementation.
 *
 * @author Philip Helger
 */
public final class StringChecker
{
  private final RegistryRepertoireDictionary m_aRrd;

  /**
   * @param aRrd
   *        Pre-built registry &rarr; repertoire dictionary. Never
   *        <code>null</code>.
   */
  public StringChecker (@NonNull final RegistryRepertoireDictionary aRrd)
  {
    m_aRrd = aRrd;
  }

  // ----------------------------------------------------------------------
  // Leaf helpers (no internal dependencies)
  // ----------------------------------------------------------------------

  private static int _resolve (@Nullable final Integer aOverride, final int nInherited)
  {
    return aOverride == null ? nInherited : aOverride.intValue ();
  }

  @NonNull
  private static EThreeValuedBoolean _unionStep (@NonNull final EThreeValuedBoolean a,
                                                 @NonNull final EThreeValuedBoolean b)
  {
    if (a == EThreeValuedBoolean.TRUE || b == EThreeValuedBoolean.TRUE)
      return EThreeValuedBoolean.TRUE;
    if (a == EThreeValuedBoolean.FALSE)
      return b;
    if (b == EThreeValuedBoolean.FALSE)
      return a;
    return EThreeValuedBoolean.UNKNOWN;
  }

  @NonNull
  private static EThreeValuedBoolean _intersectionStep (@NonNull final EThreeValuedBoolean a,
                                                        @NonNull final EThreeValuedBoolean b)
  {
    if (a == EThreeValuedBoolean.FALSE || b == EThreeValuedBoolean.FALSE)
      return EThreeValuedBoolean.FALSE;
    if (a == EThreeValuedBoolean.TRUE)
      return b;
    if (b == EThreeValuedBoolean.TRUE)
      return a;
    return EThreeValuedBoolean.UNKNOWN;
  }

  // ----------------------------------------------------------------------
  // Set-operation helpers (depend on check() recursively + the step
  // helpers above)
  // ----------------------------------------------------------------------

  @NonNull
  private EThreeValuedBoolean _union (@NonNull final List <ICREPDLNode> aChildren,
                                      @NonNull final String sChar,
                                      final int nMin,
                                      final int nMax)
  {
    if (aChildren.isEmpty ())
      return EThreeValuedBoolean.FALSE;
    EThreeValuedBoolean eAcc = EThreeValuedBoolean.FALSE;
    for (final ICREPDLNode aChild : aChildren)
    {
      final EThreeValuedBoolean eCur = check (aChild, sChar, nMin, nMax);
      eAcc = _unionStep (eAcc, eCur);
      if (eAcc == EThreeValuedBoolean.TRUE)
        return EThreeValuedBoolean.TRUE;
    }
    return eAcc;
  }

  @NonNull
  private EThreeValuedBoolean _intersection (@NonNull final List <ICREPDLNode> aChildren,
                                             @NonNull final String sChar,
                                             final int nMin,
                                             final int nMax)
  {
    if (aChildren.isEmpty ())
      return EThreeValuedBoolean.TRUE;
    EThreeValuedBoolean eAcc = EThreeValuedBoolean.TRUE;
    for (final ICREPDLNode aChild : aChildren)
    {
      final EThreeValuedBoolean eCur = check (aChild, sChar, nMin, nMax);
      eAcc = _intersectionStep (eAcc, eCur);
      if (eAcc == EThreeValuedBoolean.FALSE)
        return EThreeValuedBoolean.FALSE;
    }
    return eAcc;
  }

  @NonNull
  private EThreeValuedBoolean _difference (@NonNull final List <ICREPDLNode> aChildren,
                                           @NonNull final String sChar,
                                           final int nMin,
                                           final int nMax)
  {
    if (aChildren.isEmpty ())
      throw new IllegalStateException ("<difference> requires at least one child");
    final EThreeValuedBoolean eFirst = check (aChildren.get (0), sChar, nMin, nMax);
    final List <ICREPDLNode> aRest = aChildren.subList (1, aChildren.size ());
    final EThreeValuedBoolean eRest = _union (aRest, sChar, nMin, nMax);
    if (eFirst == EThreeValuedBoolean.FALSE)
      return EThreeValuedBoolean.FALSE;
    if (eFirst == EThreeValuedBoolean.TRUE)
    {
      return switch (eRest)
      {
        case FALSE -> EThreeValuedBoolean.TRUE;
        case TRUE -> EThreeValuedBoolean.FALSE;
        case UNKNOWN -> EThreeValuedBoolean.UNKNOWN;
      };
    }
    // first is UNKNOWN
    return eRest == EThreeValuedBoolean.TRUE ? EThreeValuedBoolean.FALSE : EThreeValuedBoolean.UNKNOWN;
  }

  // ----------------------------------------------------------------------
  // Public API
  // ----------------------------------------------------------------------

  /**
   * Evaluate the candidate string against the full tree, propagating the
   * effective UCS version window.
   *
   * @param aRoot
   *        The expanded root node.
   * @param sChar
   *        The candidate string.
   * @param nMin
   *        Inherited minimum UCS version (encoded).
   * @param nMax
   *        Inherited maximum UCS version (encoded).
   * @return Three-valued result. Never <code>null</code>.
   */
  @NonNull
  public EThreeValuedBoolean check (@NonNull final ICREPDLNode aRoot,
                                    @NonNull final String sChar,
                                    final int nMin,
                                    final int nMax)
  {
    final int nEffMin = _resolve (aRoot.getMinUcsVersion (), nMin);
    final int nEffMax = _resolve (aRoot.getMaxUcsVersion (), nMax);

    if (aRoot instanceof final CREPDLUnion aU)
      return _union (aU.children (), sChar, nEffMin, nEffMax);
    if (aRoot instanceof final CREPDLIntersection aI)
      return _intersection (aI.children (), sChar, nEffMin, nEffMax);
    if (aRoot instanceof final CREPDLDifference aD)
      return _difference (aD.children (), sChar, nEffMin, nEffMax);
    if (aRoot instanceof final CREPDLRepertoire aRep)
      return m_aRrd.get (aRep.registry ()).check (sChar);
    if (aRoot instanceof final CREPDLChar aChar)
      return CharMatcher.check (sChar, aChar);
    if (aRoot instanceof final CREPDLRef aRef)
    {
      if (aRef.children ().size () != 1)
        throw new IllegalStateException ("Unexpanded <ref>");
      return check (aRef.children ().get (0), sChar, nEffMin, nEffMax);
    }
    throw new IllegalStateException ("Unknown node " + aRoot.getClass ().getName ());
  }
}
