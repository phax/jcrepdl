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

import java.net.URI;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.crepdl.EMode;

/**
 * The <code>&lt;ref href="..."&gt;</code> CREPDL element. After expansion the referenced script is
 * parsed into {@link #children()} (a single root element).
 *
 * @param mode
 *        Optional mode attribute.
 * @param minUcsVersion
 *        Optional <code>@minUcsVersion</code>.
 * @param maxUcsVersion
 *        Optional <code>@maxUcsVersion</code>.
 * @param href
 *        Absolute URI of the referenced script. Never <code>null</code>.
 * @param children
 *        After expansion, a single-element list with the referenced subtree. Before expansion this
 *        is empty.
 * @author Philip Helger
 */
public record CREPDLRef (@Nullable EMode mode,
                         @Nullable Integer minUcsVersion,
                         @Nullable Integer maxUcsVersion,
                         @NonNull URI href,
                         @NonNull List <ICREPDLNode> children) implements ICREPDLNode
{
  public CREPDLRef
  {
    if (href == null)
      throw new IllegalArgumentException ("href must not be null");
    if (children == null)
      throw new IllegalArgumentException ("children must not be null");
    children = List.copyOf (children);
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
