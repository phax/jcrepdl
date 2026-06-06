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
package com.helger.crepdl.repertoire;

import org.jspecify.annotations.NonNull;

import com.helger.crepdl.EThreeValuedBoolean;

/**
 * A character repertoire. Calls {@link #check(String)} per character / grapheme
 * cluster string.
 *
 * @author Philip Helger
 */
@FunctionalInterface
public interface IRepertoire
{
  /**
   * @param sChar
   *        The character (or grapheme cluster) string. Never <code>null</code>.
   * @return The three-valued check result. Never <code>null</code>.
   */
  @NonNull
  EThreeValuedBoolean check (@NonNull String sChar);
}
