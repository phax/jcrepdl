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
package com.helger.crepdl.parse;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.helger.crepdl.CCREPDL;
import com.helger.crepdl.EMode;
import com.helger.crepdl.model.CREPDLChar;
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

/**
 * Parses a CREPDL XML document into an {@link ICREPDLNode} tree.
 * <p>
 * The reader is intentionally strict: it validates the document against the structural rules of
 * ISO/IEC 19757-7 rather than silently ignoring deviations. Unknown elements, unknown attributes,
 * illegal nesting, and the wrong root namespace all raise {@link CREPDLParseException} so that
 * upstream schema mistakes surface immediately instead of degrading validation results later.
 * </p>
 * <ul>
 * <li>The root namespace is {@link CCREPDL#NAMESPACE_URI_V2}.</li>
 * <li>Only known elements are allowed; unknown locals throw {@link CREPDLParseException}.</li>
 * <li>Only known attributes are allowed.</li>
 * <li><code>&lt;repertoire&gt;</code> has no CREPDL children.</li>
 * <li><code>&lt;char&gt;</code> has at most kernel/hull children, in that order.</li>
 * </ul>
 *
 * @author Philip Helger
 */
public final class CREPDLReader
{
  private static final Logger LOGGER = LoggerFactory.getLogger (CREPDLReader.class);

  // The three attributes every CREPDL element accepts. Listed once here so
  // _checkAttrs() can validate every element against the same baseline plus
  // any element-specific additions (e.g. @href on <ref>).
  private static final Set <String> ALLOWED_COMMON_ATTRS = Set.of (CCREPDL.ATTR_MODE,
                                                                   CCREPDL.ATTR_MIN_UCS_VERSION,
                                                                   CCREPDL.ATTR_MAX_UCS_VERSION);

  // Routes SAX warnings/errors through LOGGER instead of the JDK default
  // (which writes to System.err and would also trip forbiddenapis if anyone
  // leaned on it). error() and fatalError() rethrow so the parser stays
  // strict — recoverable errors must not silently degrade the parse result.
  private static final class LoggingErrorHandler implements ErrorHandler
  {
    @NonNull
    private static String _format (@NonNull final SAXParseException ex)
    {
      final StringBuilder aSB = new StringBuilder ();
      final String sSysId = ex.getSystemId ();
      if (sSysId != null && !sSysId.isEmpty ())
        aSB.append ('[').append (sSysId).append ("] ");
      final int nLine = ex.getLineNumber ();
      final int nCol = ex.getColumnNumber ();
      if (nLine > 0)
        aSB.append ("line ").append (nLine);
      if (nCol > 0)
      {
        if (nLine > 0)
          aSB.append (", ");
        aSB.append ("column ").append (nCol);
      }
      if (nLine > 0 || nCol > 0)
        aSB.append (": ");
      aSB.append (ex.getMessage ());
      return aSB.toString ();
    }

    public void warning (@NonNull final SAXParseException ex)
    {
      LOGGER.warn ("XML warning: " + _format (ex));
    }

    public void error (@NonNull final SAXParseException ex) throws SAXException
    {
      LOGGER.error ("XML error: " + _format (ex));
      throw ex;
    }

    public void fatalError (@NonNull final SAXParseException ex) throws SAXException
    {
      LOGGER.error ("XML fatal error: " + _format (ex));
      throw ex;
    }
  }

  private static final ErrorHandler LOGGING_ERROR_HANDLER = new LoggingErrorHandler ();

  private CREPDLReader ()
  {}

  private static void _setFeature (@NonNull final DocumentBuilderFactory aFactory,
                                   @NonNull final String sFeature,
                                   final boolean bValue)
  {
    try
    {
      aFactory.setFeature (sFeature, bValue);
    }
    catch (final ParserConfigurationException ex)
    {
      LOGGER.warn ("Failed to set feature " +
                   sFeature +
                   " to " +
                   bValue +
                   " on XML DocumentBuilderFactory: " +
                   ex.getMessage ());
    }
  }

  // Namespace-aware DocumentBuilder with the secure-processing feature on
  // and external DTDs disallowed. CREPDL scripts have no need for DOCTYPE
  // declarations, and allowing them would expose the parser to XXE-style
  // attacks via external entities. The feature requests are best-effort:
  // a parser implementation may not support every feature, in which case
  // we silently fall back to the default.
  @NonNull
  private static DocumentBuilder _createBuilder () throws ParserConfigurationException
  {
    final DocumentBuilderFactory aDBF = DocumentBuilderFactory.newInstance ();
    aDBF.setNamespaceAware (true);
    /*
     * Secure processing is enabled by default since JDK 8. See class
     * "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl" field "fSecure" is
     * initially "true". However, if someone uses an external XML parser library (like Xerces) it
     * might be disabled.
     */
    _setFeature (aDBF, XMLConstants.FEATURE_SECURE_PROCESSING, true);
    _setFeature (aDBF, "http://apache.org/xml/features/disallow-doctype-decl", true);
    _setFeature (aDBF, "http://xml.org/sax/features/external-general-entities", false);
    _setFeature (aDBF, "http://xml.org/sax/features/external-parameter-entities", false);
    _setFeature (aDBF, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    aDBF.setNamespaceAware (true);
    // No DTD/XSD is supplied by this project and DOCTYPE is disallowed, so
    // validating mode would only ever produce a spurious "no grammar found"
    // error on every parse.
    aDBF.setValidating (false);
    aDBF.setIgnoringElementContentWhitespace (false);
    aDBF.setExpandEntityReferences (true);
    aDBF.setIgnoringComments (true);
    aDBF.setCoalescing (true);
    try
    {
      aDBF.setXIncludeAware (false);
    }
    catch (final UnsupportedOperationException ex)
    {
      // Ignore
    }
    final DocumentBuilder aBuilder = aDBF.newDocumentBuilder ();
    aBuilder.setErrorHandler (LOGGING_ERROR_HANDLER);
    return aBuilder;
  }

  // CREPDL attributes are unprefixed and therefore live in the "no
  // namespace" partition. Using getAttributeNS(null, ...) avoids matching
  // an attribute with the same local name but a different namespace
  // (e.g. xml:base or a foreign-namespace annotation).
  @Nullable
  private static String _trimAttr (@NonNull final Element aElem, @NonNull final String sName)
  {
    if (!aElem.hasAttributeNS (null, sName))
      return null;
    return aElem.getAttributeNS (null, sName).trim ();
  }

  @Nullable
  private static Integer _parseIntOrNull (@Nullable final String s)
  {
    if (s == null || s.isEmpty ())
      return null;

    try
    {
      return Integer.valueOf (Integer.parseInt (s));
    }
    catch (final NumberFormatException ex)
    {
      throw new CREPDLParseException ("Not a number '" + s + "'", ex);
    }
  }

  // Element children only — text nodes (whitespace between elements) and
  // any element outside the CREPDL namespace are silently dropped here.
  // Foreign-namespace annotations are a deliberate extension point in the
  // spec, so they must not raise an error.
  @NonNull
  private static List <Element> _collectCrepdlChildren (@NonNull final Element aElem)
  {
    final List <Element> aRet = new ArrayList <> ();
    final NodeList aNodes = aElem.getChildNodes ();
    for (int i = 0; i < aNodes.getLength (); i++)
    {
      final Node n = aNodes.item (i);
      if (n.getNodeType () == Node.ELEMENT_NODE && CCREPDL.NAMESPACE_URI_V2.equals (n.getNamespaceURI ()))
        aRet.add ((Element) n);
    }
    return aRet;
  }

  private static void _checkAttrs (@NonNull final Element aElem, @NonNull final Set <String> aAdditional)
  {
    final NamedNodeMap aAtts = aElem.getAttributes ();
    for (int i = 0; i < aAtts.getLength (); i++)
    {
      final Node aAtt = aAtts.item (i);

      // Attributes in any namespace (e.g. xml:lang, xsi:*, custom
      // extension namespaces) are ignored — only the no-namespace
      // attributes defined by CREPDL itself are validated.
      final String sNs = aAtt.getNamespaceURI ();
      if (sNs != null && !sNs.isEmpty ())
        continue;

      // Namespace declarations look like attributes in DOM but are not
      // structural attributes per "Namespaces in XML". DOM reports them
      // either with getLocalName() returning null and getNodeName() being
      // "xmlns" / "xmlns:foo", or with both set, depending on the
      // parser — handle both cases.
      final String sLocal = aAtt.getLocalName () != null ? aAtt.getLocalName () : aAtt.getNodeName ();
      if ("xmlns".equals (sLocal) || (aAtt.getNodeName () != null && aAtt.getNodeName ().startsWith ("xmlns:")))
        continue;

      if (ALLOWED_COMMON_ATTRS.contains (sLocal))
        continue;

      if (aAdditional.contains (sLocal))
        continue;

      throw new CREPDLParseException ("An illegal attribute '" + sLocal + "'");
    }
  }

  // Base URI resolution for <ref href="...">:
  // 1. Absolute href -> use as-is, no base needed.
  // 2. Element's own baseURI -> set by the XML parser from the document
  // system id and any xml:base attributes in scope. This is the precise base when reading from a
  // file or URI.
  // 3. Caller-supplied aBaseUri-> used when the document came from a
  // String/Reader/InputStream without a known system id.
  // 4. Otherwise -> return the raw relative URI; resolution
  // happens later or fails loudly when the ref is followed.
  @NonNull
  private static URI _resolveHref (@NonNull final String sHref,
                                   @Nullable final URI aBaseUri,
                                   @NonNull final Element aElem)
  {
    try
    {
      final URI aRaw = new URI (sHref);
      if (aRaw.isAbsolute ())
        return aRaw;
      final String sElemBase = aElem.getBaseURI ();
      if (sElemBase != null && !sElemBase.isEmpty ())
        return new URI (sElemBase).resolve (aRaw);
      if (aBaseUri != null)
        return aBaseUri.resolve (aRaw);
      return aRaw;
    }
    catch (final URISyntaxException ex)
    {
      throw new CREPDLParseException ("Illegal href '" + sHref + "'", ex);
    }
  }

  // ----------------------------------------------------------------------
  // Attribute-level helpers (depend on _trimAttr)
  // ----------------------------------------------------------------------

  @Nullable
  private static EMode _parseMode (@NonNull final Element aElem)
  {
    final String sMode = _trimAttr (aElem, CCREPDL.ATTR_MODE);
    if (sMode == null)
      return null;
    final EMode e = EMode.getFromIDOrNull (sMode);
    if (e == null)
      throw new CREPDLParseException ("Illegal value of @mode '" + sMode + "'");
    return e;
  }

  @Nullable
  private static Integer _parseVersion (@NonNull final Element aElem, @NonNull final String sAttr)
  {
    final String s = _trimAttr (aElem, sAttr);
    if (s == null)
      return null;
    return Integer.valueOf (CCREPDL.versionString2Int (s));
  }

  // The four registries map 1:1 onto the sealed Registry record types in
  // the model package. Each branch enforces the registry-specific
  // required-attribute combination (e.g. IVD demands @name, ISO/10646
  // demands at least one of @name / @number) BEFORE constructing the
  // record, so the records can keep their own invariants minimal.
  @NonNull
  private static IRegistry _parseRegistry (@NonNull final Element aElem)
  {
    final String sRegistry = _trimAttr (aElem, CCREPDL.ATTR_REGISTRY);
    if (sRegistry == null)
      throw new CREPDLParseException ("@registry is missing on <repertoire>");
    final String sName = _trimAttr (aElem, CCREPDL.ATTR_NAME);
    final String sNumber = _trimAttr (aElem, CCREPDL.ATTR_NUMBER);
    final String sVersion = _trimAttr (aElem, CCREPDL.ATTR_VERSION);

    return switch (sRegistry)
    {
      case CCREPDL.REGISTRY_10646 ->
      {
        if (sName == null && sNumber == null)
          throw new CREPDLParseException ("Both @name and @number are missing on <repertoire registry=\"10646\">");
        yield new RegistryISO10646 (sName, _parseIntOrNull (sNumber));
      }
      case CCREPDL.REGISTRY_CLDR -> new RegistryCLDR (sVersion, sName);
      case CCREPDL.REGISTRY_IANA ->
      {
        if (sName == null && sNumber == null)
          throw new CREPDLParseException ("Both @name and @number are missing on <repertoire registry=\"IANA\">");
        yield new RegistryIANA (sName, _parseIntOrNull (sNumber));
      }
      case CCREPDL.REGISTRY_IVD ->
      {
        if (sName == null)
          throw new CREPDLParseException ("@name is missing on <repertoire registry=\"IVD\">");
        yield new RegistryIVD (sName);
      }
      default -> throw new CREPDLParseException ("Undefined registry '" + sRegistry + "'");
    };
  }

  // The <char> element has exactly four legal shapes per the spec:
  // (1) text only -> kernel == hull == text
  // (2) <kernel> only -> non-matching chars are UNKNOWN, not FALSE
  // (3) <hull> only -> matching chars are UNKNOWN, not TRUE
  // (4) <kernel><hull> -> classical three-valued approximation
  // The two child elements MUST appear in kernel-then-hull order; reversing
  // them is a parse error. Each leaf text is run through CodePointSyntax to
  // accept both the F# v2.0 regex dialect and the 2020 U+XXXX[-U+YYYY]
  // dialect transparently (see README sec. 2.5).
  @NonNull
  private static CREPDLChar _parseChar (@NonNull final Element aElem,
                                        @Nullable final EMode eMode,
                                        @Nullable final Integer nMin,
                                        @Nullable final Integer nMax)
  {
    final List <Element> aKids = _collectCrepdlChildren (aElem);
    if (aKids.isEmpty ())
    {
      // Plain content: either ISO/IEC 19757-7:2020 code-point literals
      // (e.g. "U+0020-U+007E") or a regex.
      final String sText = CodePointSyntax.translateOrPassThrough (aElem.getTextContent ());
      return new CREPDLChar (eMode, nMin, nMax, sText, sText, true);
    }
    if (aKids.size () == 1)
    {
      final Element aOnly = aKids.get (0);
      final String sLocal = aOnly.getLocalName ();
      if (CCREPDL.ELEMENT_KERNEL.equals (sLocal))
        return new CREPDLChar (eMode,
                               nMin,
                               nMax,
                               CodePointSyntax.translateOrPassThrough (aOnly.getTextContent ()),
                               null,
                               false);

      if (CCREPDL.ELEMENT_HULL.equals (sLocal))
        return new CREPDLChar (eMode,
                               nMin,
                               nMax,
                               null,
                               CodePointSyntax.translateOrPassThrough (aOnly.getTextContent ()),
                               false);

      throw new CREPDLParseException ("Illegal child of <char> '" + sLocal + "'");
    }

    if (aKids.size () == 2)
    {
      final Element aK = aKids.get (0);
      final Element aH = aKids.get (1);
      if (CCREPDL.ELEMENT_KERNEL.equals (aK.getLocalName ()) && CCREPDL.ELEMENT_HULL.equals (aH.getLocalName ()))
      {
        final String sKRaw = aK.getTextContent ();
        final String sHRaw = aH.getTextContent ();
        final String sK = CodePointSyntax.translateOrPassThrough (sKRaw);
        final String sH = CodePointSyntax.translateOrPassThrough (sHRaw);
        // Equality is on the *translated* form so two identical code-point
        // literals still short-circuit as kernel == hull.
        return new CREPDLChar (eMode, nMin, nMax, sK, sH, sK.equals (sH));
      }
    }
    throw new CREPDLParseException ("Illegal content of a <char> element");
  }

  // ----------------------------------------------------------------------
  // Element-level helpers (mutually recursive: _parseElement <->
  // _parseCrepdlChildren)
  // ----------------------------------------------------------------------

  @NonNull
  private static List <ICREPDLNode> _parseCrepdlChildren (@NonNull final Element aElem, @Nullable final URI aBaseUri)
  {
    final List <Element> aKids = _collectCrepdlChildren (aElem);
    final List <ICREPDLNode> aRet = new ArrayList <> (aKids.size ());
    for (final Element aKid : aKids)
      aRet.add (_parseElement (aKid, aBaseUri));
    return aRet;
  }

  // Recursive descent on the CREPDL grammar. The switch covers exactly the
  // six concrete subtypes of ICREPDLNode; the sealed hierarchy means any
  // future addition will produce a compile-time warning here.
  @NonNull
  private static ICREPDLNode _parseElement (@NonNull final Element aElem, @Nullable final URI aBaseUri)
  {
    // Defensive: callers reach this method via _parseCrepdlChildren (which
    // already namespace-filters) OR via parseRoot (a public entry point
    // that can be handed any DOM element by the caller). The latter is
    // why we re-check here instead of trusting an invariant.
    if (!CCREPDL.NAMESPACE_URI_V2.equals (aElem.getNamespaceURI ()))
      throw new CREPDLParseException ("Unexpected element in non-CREPDL namespace '" +
                                      aElem.getNamespaceURI () +
                                      "' (local name '" +
                                      aElem.getLocalName () +
                                      "')");

    final String sLocal = aElem.getLocalName ();
    final EMode eMode = _parseMode (aElem);
    final Integer nMin = _parseVersion (aElem, CCREPDL.ATTR_MIN_UCS_VERSION);
    final Integer nMax = _parseVersion (aElem, CCREPDL.ATTR_MAX_UCS_VERSION);

    return switch (sLocal)
    {
      case CCREPDL.ELEMENT_UNION ->
      {
        _checkAttrs (aElem, Set.of ());
        yield new CREPDLUnion (eMode, nMin, nMax, _parseCrepdlChildren (aElem, aBaseUri));
      }
      case CCREPDL.ELEMENT_INTERSECTION ->
      {
        _checkAttrs (aElem, Set.of ());
        yield new CREPDLIntersection (eMode, nMin, nMax, _parseCrepdlChildren (aElem, aBaseUri));
      }
      case CCREPDL.ELEMENT_DIFFERENCE ->
      {
        _checkAttrs (aElem, Set.of ());
        yield new CREPDLDifference (eMode, nMin, nMax, _parseCrepdlChildren (aElem, aBaseUri));
      }
      case CCREPDL.ELEMENT_REF ->
      {
        _checkAttrs (aElem, Set.of (CCREPDL.ATTR_HREF));
        final String sHref = _trimAttr (aElem, CCREPDL.ATTR_HREF);
        if (sHref == null)
          throw new CREPDLParseException ("@href is missing on <ref>");
        final URI aHref = _resolveHref (sHref, aBaseUri, aElem);
        yield new CREPDLRef (eMode, nMin, nMax, aHref, List.of ());
      }
      case CCREPDL.ELEMENT_REPERTOIRE ->
      {
        _checkAttrs (aElem,
                     Set.of (CCREPDL.ATTR_REGISTRY, CCREPDL.ATTR_NAME, CCREPDL.ATTR_NUMBER, CCREPDL.ATTR_VERSION));
        if (!_collectCrepdlChildren (aElem).isEmpty ())
          throw new CREPDLParseException ("<repertoire> must not have CREPDL children");
        yield new CREPDLRepertoire (eMode, nMin, nMax, _parseRegistry (aElem));
      }
      case CCREPDL.ELEMENT_CHAR ->
      {
        _checkAttrs (aElem, Set.of ());
        yield _parseChar (aElem, eMode, nMin, nMax);
      }
      default -> throw new CREPDLParseException ("Illegal element '" + sLocal + "'");
    };
  }

  // ----------------------------------------------------------------------
  // Public API
  // ----------------------------------------------------------------------

  /**
   * Parse the given (already-loaded) root element. Public so callers can hand in a DOM element from
   * any source.
   *
   * @param aRoot
   *        Root element, never <code>null</code>.
   * @param aBaseUri
   *        Optional base URI for <code>ref</code> resolution.
   * @return The parsed root node, never <code>null</code>.
   */
  @NonNull
  public static ICREPDLNode parseRoot (@NonNull final Element aRoot, @Nullable final URI aBaseUri)
  {
    final String sNs = aRoot.getNamespaceURI ();
    if (!CCREPDL.NAMESPACE_URI_V2.equals (sNs))
      throw new CREPDLParseException ("Illegal namespace '" + sNs + "' (expected '" + CCREPDL.NAMESPACE_URI_V2 + "')");
    return _parseElement (aRoot, aBaseUri);
  }

  /**
   * Parse a CREPDL document from a URI. The base URI of the parsed root element is set to
   * <code>aUri</code> so that nested <code>&lt;ref href="..."&gt;</code> elements resolve
   * correctly.
   *
   * @param aUri
   *        Absolute URI of the script. Never <code>null</code>.
   * @return The parsed root node. Never <code>null</code>.
   * @throws CREPDLParseException
   *         on syntax errors or I/O failures.
   */
  @NonNull
  public static ICREPDLNode readScript (@NonNull final URI aUri)
  {
    try
    {
      final DocumentBuilder aBuilder = _createBuilder ();
      // Passing the URI string as the system id sets baseURI on every
      // parsed element, which _resolveHref consults so <ref href="..."/>
      // resolves relative to the loaded document rather than to the JVM's
      // working directory.
      final Document aDoc = aBuilder.parse (aUri.toString ());
      final Element aRoot = aDoc.getDocumentElement ();
      return parseRoot (aRoot, aUri);
    }
    catch (final IOException | SAXException | ParserConfigurationException ex)
    {
      // I/O and XML well-formedness errors collapse into the same
      // exception type so callers have a single failure mode for the
      // whole "load and parse" operation.
      throw new CREPDLParseException ("Failed to read CREPDL script from '" + aUri + "' (" + ex.getMessage () + ")",
                                      ex);
    }
  }

  /**
   * Parse a CREPDL document from an in-memory string. The optional <code>aBaseUri</code> is used to
   * resolve <code>ref</code> elements relatively; passing <code>null</code> means absolute hrefs
   * only.
   *
   * @param sXml
   *        XML source, never <code>null</code>.
   * @param aBaseUri
   *        Optional base URI for <code>ref</code> resolution.
   * @return The parsed root node. Never <code>null</code>.
   */
  @NonNull
  public static ICREPDLNode readScriptFromString (@NonNull final String sXml, @Nullable final URI aBaseUri)
  {
    try
    {
      final DocumentBuilder aBuilder = _createBuilder ();
      final InputSource aIn = new InputSource (new StringReader (sXml));
      // setSystemId here is what makes <ref href="..."/> resolvable when
      // the document is supplied as a String — without it the parser has
      // no notion of "where this document is", so any relative href would
      // be irrecoverable.
      if (aBaseUri != null)
        aIn.setSystemId (aBaseUri.toString ());
      final Document aDoc = aBuilder.parse (aIn);
      return parseRoot (aDoc.getDocumentElement (), aBaseUri);
    }
    catch (final IOException | SAXException | ParserConfigurationException ex)
    {
      throw new CREPDLParseException ("Failed to parse CREPDL XML", ex);
    }
  }

  /**
   * Parse a CREPDL document from a {@link Reader}.
   *
   * @param aReader
   *        XML reader, never <code>null</code>.
   * @param aBaseUri
   *        Optional base URI.
   * @return The parsed root node. Never <code>null</code>.
   */
  @NonNull
  public static ICREPDLNode readScript (@NonNull final Reader aReader, @Nullable final URI aBaseUri)
  {
    try
    {
      final DocumentBuilder aBuilder = _createBuilder ();
      final InputSource aIn = new InputSource (aReader);
      if (aBaseUri != null)
        aIn.setSystemId (aBaseUri.toString ());
      final Document aDoc = aBuilder.parse (aIn);
      return parseRoot (aDoc.getDocumentElement (), aBaseUri);
    }
    catch (final IOException | SAXException | ParserConfigurationException ex)
    {
      throw new CREPDLParseException ("Failed to parse CREPDL XML", ex);
    }
  }

  /**
   * Parse a CREPDL document from an {@link InputStream}.
   *
   * @param aIS
   *        XML input stream, never <code>null</code>.
   * @param aBaseUri
   *        Optional base URI.
   * @return The parsed root node. Never <code>null</code>.
   */
  @NonNull
  public static ICREPDLNode readScript (@NonNull final InputStream aIS, @Nullable final URI aBaseUri)
  {
    try
    {
      final DocumentBuilder aBuilder = _createBuilder ();
      final InputSource aIn = new InputSource (aIS);
      if (aBaseUri != null)
        aIn.setSystemId (aBaseUri.toString ());
      final Document aDoc = aBuilder.parse (aIn);
      return parseRoot (aDoc.getDocumentElement (), aBaseUri);
    }
    catch (final IOException | SAXException | ParserConfigurationException ex)
    {
      throw new CREPDLParseException ("Failed to parse CREPDL XML", ex);
    }
  }
}
