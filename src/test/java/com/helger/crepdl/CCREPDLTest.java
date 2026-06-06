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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class CCREPDLTest
{
  @Test
  void testVersionString2Int ()
  {
    assertEquals (20000, CCREPDL.versionString2Int ("2"));
    assertEquals (20100, CCREPDL.versionString2Int ("2.1"));
    assertEquals (20103, CCREPDL.versionString2Int ("2.1.3"));
    assertEquals (110000, CCREPDL.versionString2Int ("11.0"));
    assertThrows (IllegalArgumentException.class, () -> CCREPDL.versionString2Int ("1.2.3.4"));
    assertThrows (IllegalArgumentException.class, () -> CCREPDL.versionString2Int ("x"));
  }
}
