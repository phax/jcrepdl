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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.id.IHasID;
import com.helger.base.lang.EnumHelper;

/**
 * The CREPDL evaluation mode: either character-based or grapheme-cluster-based. Maps to the
 * <code>mode</code> attribute on a CREPDL element.
 *
 * @author Philip Helger
 */
public enum EMode implements IHasID <String>
{
  CHARACTER (CCREPDL.MODE_CHARACTER),
  GRAPHEME_CLUSTER (CCREPDL.MODE_GRAPHEME_CLUSTER);

  private final String m_sID;

  EMode (@NonNull final String sID)
  {
    m_sID = sID;
  }

  @NonNull
  public String getID ()
  {
    return m_sID;
  }

  @Nullable
  public static EMode getFromIDOrNull (@Nullable final String sID)
  {
    return EnumHelper.getFromIDOrNull (EMode.class, sID);
  }
}
