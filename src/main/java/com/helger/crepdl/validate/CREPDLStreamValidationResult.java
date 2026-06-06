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

import org.jspecify.annotations.NonNull;

/**
 * Result of validating a text stream: two arrays. {@link #unknowns()}
 * collects characters or grapheme clusters that <em>may or may not</em> be in
 * the repertoire (kernel/hull disagreement). {@link #notIncluded()} collects
 * characters or grapheme clusters that are definitely <em>not</em> in the
 * repertoire.
 *
 * @param unknowns
 *        Strings that produced {@code UNKNOWN}.
 * @param notIncluded
 *        Strings that produced {@code FALSE}.
 * @author Philip Helger
 */
public record CREPDLStreamValidationResult (@NonNull String [] unknowns, @NonNull String [] notIncluded)
{
  public CREPDLStreamValidationResult
  {
    if (unknowns == null || notIncluded == null)
      throw new IllegalArgumentException ("arrays must not be null");
  }
}
