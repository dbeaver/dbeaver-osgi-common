package com.dbeaver.osgi.dependency.processing.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public interface ContentParserXmlExtension {
    void startElement(String uri, String localName, String qualifiedName, Attributes attributes,
        ContentFileHandler.ParserState currentState,
        ContentFileHandler.UnitInformation currentBundle
    ) throws SAXException;
    void endElement(String uri, String localName, String qualifiedName,
        ContentFileHandler.ParserState currentState,
        ContentFileHandler.UnitInformation currentBundle
    );
}
