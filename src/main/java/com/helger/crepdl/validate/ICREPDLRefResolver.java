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

import java.net.URI;

import org.jspecify.annotations.NonNull;

import com.helger.crepdl.parse.CREPDLParseException;

/**
 * Resolves a CREPDL <code>&lt;ref href="..."/&gt;</code> URI to an XML byte
 * stream. Implementations decide whether a given URI may be dereferenced and
 * how the bytes are obtained.
 * <p>
 * The default resolver used by {@link CREPDLValidator} and
 * {@link RefAndRepertoireExpander#expand(com.helger.crepdl.model.ICREPDLNode)}
 * is {@link DenyAllRefResolver}, which refuses every URI. Callers whose CREPDL
 * scripts contain <code>&lt;ref&gt;</code> elements must explicitly opt into a
 * resolver that allows the URIs they expect &mdash; for example
 * {@link FileSystemRefResolver} for a sandboxed directory of script files, or
 * {@link UnrestrictedRefResolver} (unsafe for untrusted input) for the pre-SPI
 * behaviour.
 * </p>
 *
 * @author Philip Helger
 */
@FunctionalInterface
public interface ICREPDLRefResolver
{
  /**
   * Resolve a ref URI to an XML byte stream.
   *
   * @param aHref
   *        Absolute URI to resolve. Never <code>null</code>.
   * @return Open byte source. The caller owns the returned stream and must
   *         close it. Never <code>null</code>.
   * @throws CREPDLParseException
   *         if the URI is rejected by the resolver's policy or cannot be
   *         fetched.
   */
  @NonNull
  CREPDLRefSource resolve (@NonNull URI aHref);
}
