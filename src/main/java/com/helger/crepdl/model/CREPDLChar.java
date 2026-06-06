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
 * The <code>&lt;char&gt;</code> CREPDL element, parsed into an optional <em>kernel</em> regex and
 * an optional <em>hull</em> regex.
 * <p>
 * Combinations:
 * </p>
 * <ul>
 * <li>Plain text content: both kernel and hull are set to the same pattern and
 * <code>kernelEqualsHull</code> is <code>true</code>.</li>
 * <li><code>&lt;kernel&gt;</code> only: hull is <code>null</code>.</li>
 * <li><code>&lt;hull&gt;</code> only: kernel is <code>null</code>.</li>
 * <li>Both <code>&lt;kernel&gt;</code> and <code>&lt;hull&gt;</code>: <code>kernelEqualsHull</code>
 * is <code>true</code> only if the two patterns are textually identical.</li>
 * </ul>
 *
 * @param mode
 *        Optional mode attribute.
 * @param minUcsVersion
 *        Optional <code>@minUcsVersion</code>.
 * @param maxUcsVersion
 *        Optional <code>@maxUcsVersion</code>.
 * @param kernelRegex
 *        Kernel regex source, may be <code>null</code>.
 * @param hullRegex
 *        Hull regex source, may be <code>null</code>.
 * @param kernelEqualsHull
 *        Hint used to short-circuit evaluation when kernel and hull are identical.
 * @author Philip Helger
 */
public record CREPDLChar (@Nullable EMode mode,
                          @Nullable Integer minUcsVersion,
                          @Nullable Integer maxUcsVersion,
                          @Nullable String kernelRegex,
                          @Nullable String hullRegex,
                          boolean kernelEqualsHull) implements ICREPDLNode
{
  public CREPDLChar
  {
    if (kernelRegex == null && hullRegex == null)
      throw new IllegalArgumentException ("Either kernel or hull must be set");
  }

  @Override
  @Nullable
  public EMode getMode ()
  {
    return mode;
  }

  @Override
  @Nullable
  public Integer getMinUcsVersion ()
  {
    return minUcsVersion;
  }

  @Override
  @Nullable
  public Integer getMaxUcsVersion ()
  {
    return maxUcsVersion;
  }
}
