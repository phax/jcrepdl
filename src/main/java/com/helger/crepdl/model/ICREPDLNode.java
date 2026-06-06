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
package com.helger.crepdl.model;

import org.jspecify.annotations.Nullable;

import com.helger.crepdl.EMode;

/**
 * Sealed type representing a node in a parsed CREPDL script. Every CREPDL element carries the same
 * three optional attributes: {@link #getMode()}, {@link #getMinUcsVersion()} and
 * {@link #getMaxUcsVersion()}.
 *
 * @author Philip Helger
 */
public sealed interface ICREPDLNode permits
                                    CREPDLUnion,
                                    CREPDLIntersection,
                                    CREPDLDifference,
                                    CREPDLRef,
                                    CREPDLRepertoire,
                                    CREPDLChar
{
  /**
   * @return Optional <code>@mode</code> attribute. <code>null</code> means "inherit / default" (=
   *         {@link EMode#CHARACTER} at root).
   */
  @Nullable
  EMode getMode ();

  /**
   * @return Optional <code>@minUcsVersion</code> encoded by
   *         {@link com.helger.crepdl.CCREPDL#versionString2Int(String)}. <code>null</code> means
   *         "inherit".
   */
  @Nullable
  Integer getMinUcsVersion ();

  /**
   * @return Optional <code>@maxUcsVersion</code> encoded by
   *         {@link com.helger.crepdl.CCREPDL#versionString2Int(String)}. <code>null</code> means
   *         "inherit".
   */
  @Nullable
  Integer getMaxUcsVersion ();
}
