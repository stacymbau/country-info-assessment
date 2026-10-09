package com.assessment.country_info_service.client;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import com.assessment.country_info_service.exception.CountryNotFoundException;
import com.assessment.country_info_service.exception.ExternalServiceException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Namespace-aware, XXE-hardened parsing of the CountryInfoService SOAP responses. */
public final class SoapXmlParser {

    private static final String NOT_FOUND_PREFIX = "country not found";

    private SoapXmlParser() {
    }

    public static String parseIsoCode(String xml) {
        Document doc = parse(xml);
        failOnFault(doc);
        Element result = firstByLocalName(doc, "CountryISOCodeResult");
        if (result == null) {
            throw new ExternalServiceException("CountryISOCodeResult missing from SOAP response", null);
        }
        String value = result.getTextContent() == null ? "" : result.getTextContent().trim();
        if (!value.matches("[A-Za-z]{2}")) {
            throw new CountryNotFoundException("Country not found");
        }
        return value.toUpperCase(Locale.ROOT);
    }

    public static SoapCountryInfo parseFullCountryInfo(String xml) {
        Document doc = parse(xml);
        failOnFault(doc);
        Element result = firstByLocalName(doc, "FullCountryInfoResult");
        if (result == null) {
            throw new ExternalServiceException("FullCountryInfoResult missing from SOAP response", null);
        }
        String name = childText(result, "sName");
        if (name == null || name.isBlank() || name.toLowerCase(Locale.ROOT).startsWith(NOT_FOUND_PREFIX)) {
            throw new CountryNotFoundException("Country not found");
        }
        List<SoapCountryInfo.SoapLanguage> languages = new ArrayList<>();
        Element languagesEl = firstChild(result, "Languages");
        if (languagesEl != null) {
            NodeList children = languagesEl.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node n = children.item(i);
                if (n instanceof Element e && "tLanguage".equals(e.getLocalName())) {
                    languages.add(new SoapCountryInfo.SoapLanguage(
                            childText(e, "sISOCode"),
                            childText(e, "sName")));
                }
            }
        }
        return new SoapCountryInfo(
                childText(result, "sISOCode"),
                name,
                childText(result, "sCapitalCity"),
                childText(result, "sPhoneCode"),
                childText(result, "sContinentCode"),
                childText(result, "sCurrencyISOCode"),
                childText(result, "sCountryFlag"),
                languages);
    }

    private static Document parse(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new ExternalServiceException("Unparseable SOAP response", e);
        }
    }

    private static void failOnFault(Document doc) {
        Element fault = firstByLocalName(doc, "Fault");
        if (fault != null) {
            String reason = childText(fault, "faultstring");
            throw new ExternalServiceException(
                    "SOAP fault: " + (reason == null ? "unknown" : reason), null);
        }
    }

    private static Element firstByLocalName(Document doc, String localName) {
        NodeList list = doc.getElementsByTagNameNS("*", localName);
        return list.getLength() == 0 ? null : (Element) list.item(0);
    }

    private static Element firstChild(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element e && localName.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    private static String childText(Element parent, String localName) {
        Element e = firstChild(parent, localName);
        return e == null || e.getTextContent() == null
                ? null
                : e.getTextContent().trim();
    }
}