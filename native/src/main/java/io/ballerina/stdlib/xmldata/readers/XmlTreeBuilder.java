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
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.*;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BTypedesc;

import java.io.InputStream;
import java.util.*;

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
public class XmlTreeBuilder {

    private static final XMLInputFactory xmlInputFactory;

    private Stack<XmlChunk> chunks = new Stack<>();
    private Stack<XmlChunk.XmlStartElement> startElements = new Stack<>();
    private Stack<Type> types = new Stack<>();
    private Deque<XmlChunk> siblings = new ArrayDeque<>();
    private BTypedesc typedesc = null;

    static {
        xmlInputFactory = XMLInputFactory.newInstance();
        xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    }

    private final InputStream inputStream;
    private XMLStreamReader xmlStreamReader;

    public XmlTreeBuilder(InputStream inputStream) {
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

    public Object readAndTransform(BTypedesc typedesc) {
        try {
            xmlStreamReader = xmlInputFactory.createXMLStreamReader(inputStream);
            return parse(typedesc);
        } catch (XMLStreamException e) {
            return ErrorCreator.createError(
                    StringUtils.fromString("XMLStreamException: " + e.getMessage()));
        }
    }

    private Object parse(BTypedesc typedesc) throws RuntimeException {
        this.chunks.empty();
        this.startElements.empty();
        this.types.empty();
        this.typedesc = typedesc;
        Optional<BError> error = Optional.empty();
        try {
            while (xmlStreamReader.hasNext()) {
                int next = xmlStreamReader.next();
                switch (next) {
                    case START_ELEMENT:
                        error = createStartElement(xmlStreamReader);
                        if (error.isPresent()) {
                            return error.get();
                        }
                        break;
                    case END_ELEMENT:
                        error = createEndElement();
                        if (error.isPresent()) {
                            return error.get();
                        }
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
            return ErrorCreator.createError(
                    StringUtils.fromString("XMLStreamException: " + e.getMessage()));
        }
        return ValueCreator.createMapValue(typedesc.getDescribingType());
    }

    private Optional<BError> createStartElement(XMLStreamReader xmlStreamReader) {
        QName elemName = xmlStreamReader.getName();
        XmlChunk.XmlStartElement element = new XmlChunk.XmlStartElement(elemName);

        // Populate Attributes - Ignore Namespace declarations for now.
        int count = xmlStreamReader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            QName attributeName = xmlStreamReader.getAttributeName(i);
            element.addAttribute(attributeName.toString(), xmlStreamReader.getAttributeValue(i));
        }

        Optional<BError> error = isRoot() ? checkRootType() : getNextType(elemName.getLocalPart());
        if (error.isPresent()) {
            return error;
        }
        chunks.push(element);
        startElements.push(element);
        return Optional.empty();
    }

    private Optional<BError> createEndElement() {
        XmlChunk.XmlStartElement startElement = startElements.pop();
        chunks.push(new XmlChunk.XmlEndElement(startElement.qName));
        return Optional.empty();
    }

    private void createText(XMLStreamReader xmlStreamReader) {
        final String text = xmlStreamReader.getText();

        XmlChunk.XmlText xmlText = new XmlChunk.XmlText(text);
        chunks.push(xmlText);
    }

    private void createComment(XMLStreamReader xmlStreamReader) {
        chunks.push(new XmlChunk.XmlComment(xmlStreamReader.getText()));
    }

    private void createPI(XMLStreamReader xmlStreamReader) {
        chunks.push(new XmlChunk.XmlProcessingInstruction(xmlStreamReader.getPITarget(), xmlStreamReader.getPIData()));
    }

    private boolean isRoot() {
        return this.types.empty();
    }

    private Optional<BError> checkRootType() {
        Type describingType = this.typedesc.getDescribingType();
        if (describingType.getTag() == TypeTags.MAP_TAG
                || describingType.getTag() == TypeTags.RECORD_TYPE_TAG) {
            this.types.push(this.typedesc.getDescribingType());
            return Optional.empty();
        }
        return Optional.of(ErrorCreator.createError(
                StringUtils.fromString("Unexpected type: '" + describingType.getName() + "' at //")));
    }

    private Optional<BError> getNextType(String elementName) {
        Type type = types.peek();
        Optional<Type> nextType = getNextMemberType(type, elementName);
        if (nextType.isPresent()) {
            types.push(nextType.get());
            return Optional.empty();
        }
        String path = getCurrentPath();
        String msg;
        if (type.getName().isEmpty()) {
            msg = "Unexpected type  at " + path;
        } else {
            msg = "Unexpected type '" + type.getName() + "' at " + path;
        }
        return Optional.of(ErrorCreator.createError(
                StringUtils.fromString(msg)));
    }

    private Optional<Type> getNextMemberType(Type currentType, String elementName) {
        final int typeTag = currentType.getTag();
        switch (typeTag) {
            case TypeTags.JSON_TAG:
            case TypeTags.ANYDATA_TAG:
            case TypeTags.ANY_TAG:
                return Optional.of(currentType);
            case TypeTags.UNION_TAG:
                for (Type memberType : ((UnionType) currentType).getMemberTypes()) {
                    Optional<Type> result = getNextMemberType(memberType, elementName);
                    if (result.isPresent()) {
                        return result;  // Take First match
                    }
                }
            case TypeTags.MAP_TAG:
                return Optional.of(((MapType) currentType).getConstrainedType());
            case TypeTags.RECORD_TYPE_TAG:
                RecordType recordType = (RecordType) currentType;
                if (recordType.getFields().containsKey(elementName)) {
                    return Optional.of(recordType.getFields().get(elementName).getFieldType());
                } else if (!recordType.isSealed()) {
                    return Optional.of(recordType.getRestFieldType());
                } else {
                    return Optional.empty();
                }
            case TypeTags.ARRAY_TAG:
                ArrayType arrayType = (ArrayType) currentType;
                return Optional.of(arrayType.getElementType());
            default:
                return Optional.empty();
        }
    }

    private String getCurrentPath() {
        StringBuilder path = new StringBuilder();
        for (XmlChunk.XmlStartElement startElement : startElements) {
            path.append("/").append(startElement.qName);
        }
        return path.toString();
    }

    private Object handleMapType(MapType mapType) {
        Type constrainedType = mapType.getConstrainedType();
        boolean convertSimpleBasicTypes = false;
        if (constrainedType.getTag() == TypeTags.XML_TAG) {
            // Not supported.
            return ErrorCreator.createError(
                    StringUtils.fromString("Invalid map type descriptor. Expected map of JSON"));
        } else if (constrainedType.getTag() == TypeTags.JSON_TAG
                || constrainedType.getTag() == TypeTags.ANY_TAG
                || constrainedType.getTag() == TypeTags.ANYDATA_TAG) {
            // AnyData type and Any type considered as JSON.
            // Additionally, we try to convert simple Basic types much as possible.
            convertSimpleBasicTypes = true;
        } else if (constrainedType.getTag() == TypeTags.STRING_TAG) {
            // All are considered as string.
            convertSimpleBasicTypes = false;
        } else {
            return ErrorCreator.createError(
                    StringUtils.fromString("Invalid map type descriptor. Expected map of JSON"));
        }
        return null;
    }

}
