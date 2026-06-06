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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;

import com.helger.crepdl.model.CREPDLDifference;
import com.helger.crepdl.model.CREPDLIntersection;
import com.helger.crepdl.model.CREPDLRef;
import com.helger.crepdl.model.CREPDLRepertoire;
import com.helger.crepdl.model.CREPDLUnion;
import com.helger.crepdl.model.ICREPDLNode;
import com.helger.crepdl.model.IRegistry;
import com.helger.crepdl.model.RegistryCLDR;
import com.helger.crepdl.model.RegistryIANA;
import com.helger.crepdl.model.RegistryISO10646;
import com.helger.crepdl.model.RegistryIVD;
import com.helger.crepdl.parse.CREPDLParseException;
import com.helger.crepdl.repertoire.CodePointRangeRepertoire;
import com.helger.crepdl.repertoire.IANARepertoires;
import com.helger.crepdl.repertoire.IRepertoire;
import com.helger.crepdl.repertoire.ISO10646Collections;
import com.helger.crepdl.repertoire.IVDRepertoires;

/**
 * Caches one {@link IRepertoire} per {@link IRegistry} encountered in an
 * expanded CREPDL tree. Created at validator-construction time so each
 * subsequent string-check is amortized.
 *
 * @author Philip Helger
 */
public final class RegistryRepertoireDictionary
{
  private final Map <IRegistry, IRepertoire> m_aMap = new HashMap <> ();

  private RegistryRepertoireDictionary ()
  {}

  // ----------------------------------------------------------------------
  // Private helpers, leaf-first
  // ----------------------------------------------------------------------

  @NonNull
  private static IRepertoire _readDecimalRangesResource (@NonNull final String sPath)
  {
    try (final InputStream aIS = RegistryRepertoireDictionary.class.getResourceAsStream (sPath))
    {
      if (aIS == null)
        throw new CREPDLParseException ("Missing bundled resource '" + sPath + "'");
      return CodePointRangeRepertoire.readDecimalRanges (new InputStreamReader (aIS, StandardCharsets.UTF_8));
    }
    catch (final IOException ex)
    {
      throw new CREPDLParseException ("Failed to read '" + sPath + "'", ex);
    }
  }

  @NonNull
  private static IRepertoire _buildISO10646 (@NonNull final RegistryISO10646 aIso)
  {
    final String sName = aIso.name ();
    if (sName != null)
    {
      final String sInline = ISO10646Collections.lookupInlineByName (sName);
      if (sInline != null)
        return CodePointRangeRepertoire.fromSemicolonSpec (sInline);
      final String sOutPath = ISO10646Collections.lookupOutOfLineResource (sName);
      if (sOutPath != null)
        return _readDecimalRangesResource (sOutPath);
    }
    if (aIso.number () != null)
    {
      final int nNumber = aIso.number ().intValue ();
      final String sInline = ISO10646Collections.lookupInlineByNumber (nNumber);
      if (sInline != null)
        return CodePointRangeRepertoire.fromSemicolonSpec (sInline);
      final String sOutPath = ISO10646Collections.lookupOutOfLineResource (nNumber);
      if (sOutPath != null)
        return _readDecimalRangesResource (sOutPath);
    }
    throw new CREPDLParseException ("No such ISO 10646 collection: name='" +
                                    aIso.name () +
                                    "', number='" +
                                    aIso.number () +
                                    "'");
  }

  @NonNull
  private IRepertoire _build (@NonNull final IRegistry aRegistry)
  {
    if (aRegistry instanceof final RegistryISO10646 aIso)
      return _buildISO10646 (aIso);
    if (aRegistry instanceof final RegistryIANA aIana)
      return IANARepertoires.create (aIana);
    if (aRegistry instanceof final RegistryIVD aIvd)
      return IVDRepertoires.create (aIvd.name ());
    if (aRegistry instanceof RegistryCLDR)
      throw new CREPDLParseException ("CLDR is not supported yet");
    throw new CREPDLParseException ("Unknown registry '" + aRegistry.getClass ().getName () + "'");
  }

  private void _scan (@NonNull final ICREPDLNode aNode)
  {
    if (aNode instanceof final CREPDLUnion aU)
      for (final ICREPDLNode aC : aU.children ())
        _scan (aC);
    else if (aNode instanceof final CREPDLIntersection aI)
      for (final ICREPDLNode aC : aI.children ())
        _scan (aC);
    else if (aNode instanceof final CREPDLDifference aD)
      for (final ICREPDLNode aC : aD.children ())
        _scan (aC);
    else if (aNode instanceof final CREPDLRef aR)
      for (final ICREPDLNode aC : aR.children ())
        _scan (aC);
    else if (aNode instanceof final CREPDLRepertoire aRep)
      m_aMap.computeIfAbsent (aRep.registry (), this::_build);
    // CREPDLChar has no repertoire reference
  }

  // ----------------------------------------------------------------------
  // Public API
  // ----------------------------------------------------------------------

  /**
   * Walk the expanded tree and pre-build a repertoire for every distinct
   * {@link IRegistry} found.
   *
   * @param aRoot
   *        Expanded root, never <code>null</code>.
   * @return A new dictionary, never <code>null</code>.
   */
  @NonNull
  public static RegistryRepertoireDictionary build (@NonNull final ICREPDLNode aRoot)
  {
    final RegistryRepertoireDictionary aRet = new RegistryRepertoireDictionary ();
    aRet._scan (aRoot);
    return aRet;
  }

  /**
   * @param aRegistry
   *        The registry to look up. Never <code>null</code>.
   * @return The cached repertoire. Never <code>null</code>.
   * @throws IllegalStateException
   *         if the registry was not pre-scanned.
   */
  @NonNull
  public IRepertoire get (@NonNull final IRegistry aRegistry)
  {
    final IRepertoire aRet = m_aMap.get (aRegistry);
    if (aRet == null)
      throw new IllegalStateException ("Registry not pre-built '" + aRegistry + "'");
    return aRet;
  }
}
