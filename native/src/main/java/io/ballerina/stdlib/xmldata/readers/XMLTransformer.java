/*
 *  Copyright (c) 2023, WSO2 LLC. (https://www.wso2.com). All Rights Reserved.
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.stdlib.xmldata.readers;

import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BTypedesc;

import java.io.InputStream;
import java.util.*;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import static javax.xml.stream.XMLStreamConstants.CDATA;
import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.COMMENT;
import static javax.xml.stream.XMLStreamConstants.DTD;
import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.PROCESSING_INSTRUCTION;
import static javax.xml.stream.XMLStreamConstants.SPACE;
import static javax.xml.stream.XMLStreamConstants.START_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * TODO: Fix me.
 * <p>
 * This implementation uses fail fast strategy.
 *
 * @since 2.0.0
 */
public class XMLTransformer {

    private static final XMLInputFactory xmlInputFactory;
//    private static String XMLNS_NS_URI_PREFIX = "{" + XMLConstants.XMLNS_ATTRIBUTE_NS_URI + "}";

    private Stack<XMLChunk> chunks = new Stack<>();
    private Stack<XMLChunk.XMLStartElement> startElements = new Stack<>();
    private Deque<XMLChunk> siblings = new ArrayDeque<>();

    static {
        xmlInputFactory = XMLInputFactory.newInstance();
        xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    }

    private final InputStream inputStream;
    private XMLStreamReader xmlStreamReader;

    public XMLTransformer(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    private static Object convertBasicType(String value, Type type) throws RuntimeException {
        switch (type.getTag()) {
            case TypeTags.INT_TAG:
            case TypeTags.BYTE_TAG:
                return Long.parseLong(value);
            case TypeTags.FLOAT_TAG:
                return Double.parseDouble(value);
            case TypeTags.DECIMAL_TAG:
                return ValueCreator.createDecimalValue(value);
            case TypeTags.STRING_TAG:
                return StringUtils.fromString(value);
            case TypeTags.BOOLEAN_TAG:
                return Boolean.parseBoolean(value);
            default:
                throw new RuntimeException("Unsupported type: " + type.getName());
        }
    }

    public void readAndTransform(BTypedesc typedesc) throws RuntimeException {
        try {
            xmlStreamReader = xmlInputFactory.createXMLStreamReader(inputStream);
            parse();
        } catch (XMLStreamException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void parse() throws RuntimeException {
        try {
            while (xmlStreamReader.hasNext()) {
                int next = xmlStreamReader.next();
                switch (next) {
                    case START_ELEMENT:
                        createStartElement(xmlStreamReader);
                        break;
                    case END_ELEMENT:
                        createEndElement();
                        break;
                    case PROCESSING_INSTRUCTION:
                        createPI(xmlStreamReader);
                        break;
                    case COMMENT:
                        createComment(xmlStreamReader);
                        break;
                    case CDATA:
                    case CHARACTERS:
                        createText(xmlStreamReader);
                        break;
                    case END_DOCUMENT:
                    case START_DOCUMENT:
                    case DTD:
                    case SPACE:
                        break;
                    default:
                        assert false;
                }
            }
        } catch (Exception e) {
            handleXMLStreamException(e);
        }
    }

    private void createStartElement(XMLStreamReader xmlStreamReader) {
        QName elemName = xmlStreamReader.getName();
        XMLChunk.XMLStartElement element = new XMLChunk.XMLStartElement(elemName);

        // Populate Attributes - Ignore Namespace declarations for now.
        int count = xmlStreamReader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            QName attributeName = xmlStreamReader.getAttributeName(i);
            element.addAttribute(attributeName.toString(), xmlStreamReader.getAttributeValue(i));
        }

        chunks.push(element);
        startElements.push(element);
    }

    private void createEndElement() {
        XMLChunk.XMLStartElement startElement = startElements.pop();
        chunks.push(new XMLChunk.XMLEndElement(startElement.qName));
    }

    private void createText(XMLStreamReader xmlStreamReader) {
        final String text = xmlStreamReader.getText();

        XMLChunk.XMLText xmlText = new XMLChunk.XMLText(text);
        chunks.push(xmlText);
    }

    private void createComment(XMLStreamReader xmlStreamReader) {
        chunks.push(new XMLChunk.XMLComment(xmlStreamReader.getText()));
    }

    private void createPI(XMLStreamReader xmlStreamReader) {
        chunks.push(new XMLChunk.XMLProcessingInstruction(xmlStreamReader.getPITarget(), xmlStreamReader.getPIData()));
    }

    private void handleXMLStreamException(Exception e) throws RuntimeException {
        // todo: do e.getMessage contain all the information? verify
        throw new RuntimeException(e.getMessage(), e);
    }
}
