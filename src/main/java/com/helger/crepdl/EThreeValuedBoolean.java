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

import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;

/**
 * Internal three-valued boolean used during CREPDL evaluation. Differs from
 * {@link ECREPDLValidationResult} only in that the helpers here are intended
 * to be composed during evaluation (lazy second operand).
 *
 * @author Philip Helger
 */
public enum EThreeValuedBoolean
{
  TRUE,
  FALSE,
  UNKNOWN;

  /**
   * Set union with lazy right-hand side, matching the F# reference
   * implementation:
   * <pre>
   * True             -> True
   * False            -> rhs()
   * Unknown          -> True if rhs() = True else Unknown
   * </pre>
   *
   * @param aRhs
   *        Lazy supplier for the right-hand operand. Never <code>null</code>.
   * @return Combined value, never <code>null</code>.
   */
  @NonNull
  public EThreeValuedBoolean union (@NonNull final Supplier <EThreeValuedBoolean> aRhs)
  {
    if (this == TRUE)
      return TRUE;
    if (this == FALSE)
      return aRhs.get ();
    return aRhs.get () == TRUE ? TRUE : UNKNOWN;
  }

  /**
   * Set intersection with lazy right-hand side:
   * <pre>
   * False            -> False
   * True             -> rhs()
   * Unknown          -> False if rhs() = False else Unknown
   * </pre>
   *
   * @param aRhs
   *        Lazy supplier for the right-hand operand. Never <code>null</code>.
   * @return Combined value, never <code>null</code>.
   */
  @NonNull
  public EThreeValuedBoolean intersection (@NonNull final Supplier <EThreeValuedBoolean> aRhs)
  {
    if (this == FALSE)
      return FALSE;
    if (this == TRUE)
      return aRhs.get ();
    return aRhs.get () == FALSE ? FALSE : UNKNOWN;
  }

  /**
   * Convert to the public {@link ECREPDLValidationResult} enum.
   *
   * @return Never <code>null</code>.
   */
  @NonNull
  public ECREPDLValidationResult toValidationResult ()
  {
    return switch (this)
    {
      case TRUE -> ECREPDLValidationResult.TRUE;
      case FALSE -> ECREPDLValidationResult.FALSE;
      case UNKNOWN -> ECREPDLValidationResult.UNKNOWN;
    };
  }
}
