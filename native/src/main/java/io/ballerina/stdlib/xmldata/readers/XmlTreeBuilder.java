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

import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.flags.SymbolFlags;
import io.ballerina.runtime.api.types.*;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.*;

import java.io.InputStream;
import java.util.*;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

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
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;
import static org.ballerinalang.langlib.integer.FromString.*;

/**
 * TODO: Fix me.
 * <p>
 * This implementation uses fail fast strategy.
 *
 * @since 2.0.0
 */
public class XmlTreeBuilder {

    private static final XMLInputFactory xmlInputFactory;
    public static final String ROOT = "root";

    private final Stack<XmlChunk> chunks = new Stack<>();
    private final Stack<XmlChunk.XmlStartElement> startElements = new Stack<>();
    private final Stack<TypeMapping> types = new Stack<>();
    private final Stack<List<Map.Entry<BString, BMapInitialValueEntry>>> siblings = new Stack<>();
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
        this.siblings.empty();
        this.siblings.push(new ArrayList<>());
        Optional<BError> error;
        try {
            while (xmlStreamReader.hasNext()) {
                int next = xmlStreamReader.next();
                switch (next) {
                    case START_ELEMENT:
                        error = handleStartElement(xmlStreamReader);
                        if (error.isPresent()) {
                            return error.get();
                        }
                        break;
                    case END_ELEMENT:
                        error = handleEndElement();
                        if (error.isPresent()) {
                            return error.get();
                        }
                        break;
                    case PROCESSING_INSTRUCTION:
                        handleXmlPI(xmlStreamReader);
                        break;
                    case COMMENT:
                        handleXmlComment(xmlStreamReader);
                        break;
                    case CDATA:
                    case CHARACTERS:
                        handleXmlText(xmlStreamReader);
                        break;
                    case END_DOCUMENT:
                    case DTD:
                        break;
                    default:
                        assert false;
                }
            }
        } catch (Exception e) {
            if (e instanceof BError) {
                return e;
            }
            String reason = e.getMessage() != null ? ", " + e.getMessage() : e.getClass().getName();
            return ErrorCreator.createError(StringUtils.fromString("XML Parse Error:" + reason));
        }
        List<Map.Entry<BString, BMapInitialValueEntry>> values = this.siblings.pop();
        BMap<BString, Object> mapValue = ValueCreator.createMapValue(PredefinedTypes.TYPE_MAP, values.stream()
                                                                                                     .map(Map.Entry::getValue)
                                                                                                     .toArray(
                                                                                                             BMapInitialValueEntry[]::new));
        return mapValue.get(StringUtils.fromString(ROOT));
    }

    private Optional<BError> handleStartElement(XMLStreamReader xmlStreamReader) {
        this.siblings.push(new ArrayList<>());
        QName elemName = xmlStreamReader.getName();
        XmlChunk.XmlStartElement element = new XmlChunk.XmlStartElement(elemName);
        startElements.push(element);

        String localPart = elemName.getLocalPart();
        Optional<BError> error = isRoot() ? checkRootType(localPart) : getNextType(localPart);
        if (error.isPresent()) {
            return error;
        }

        // Populate Attributes - Ignore Namespace declarations for now.
        int count = xmlStreamReader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            QName attributeName = xmlStreamReader.getAttributeName(i);
            String attributeValue = xmlStreamReader.getAttributeValue(i);
            element.addAttribute(attributeName.toString(), attributeValue);
        }

        chunks.push(element);
        return Optional.empty();
    }

    private Optional<BError> handleEndElement() {
        List<Map.Entry<BString, BMapInitialValueEntry>> values = this.siblings.pop();

        XmlChunk.XmlStartElement startElement = startElements.pop();
        TypeMapping typeMapping = types.pop();

        List<XmlChunk.XmlText> chunkList = new ArrayList<>();
        // Attempt 1:
        // If type is value type,  there should be only one text element.
        // Can't ignore PI here. It is an error.
        // Comments supported. But if Text is sliced, type is String only
        // TODO : Ignore Attributes, because we give the priority to the element. Or Should we?
        BString jsonName = typeMapping.jsonName;
        if (TypeUtils.isValueType(typeMapping.type)) {
            while (!chunks.empty()) {
                XmlChunk chunk = chunks.pop();
                if (chunk.kind == XmlChunkKind.TEXT) {
                    chunkList.add((XmlChunk.XmlText) chunk);
                } else if (chunk.kind == XmlChunkKind.PROCESSING_INSTRUCTION) {
                    return Optional.of(
                            createTypeMappingError(typeMapping.type, "XML Processing Instruction not allowed."));
                }
                if (chunk == startElement) {
                    break;
                }
            }
            Object value;
            if (chunkList.size() > 1) {
                if (!containStringType(typeMapping.type)) {
                    return Optional.of(
                            createTypeMappingError(typeMapping.type, "XML element contains more than one text node."));
                }
                value = StringUtils.fromString(chunkList.stream().map(c -> c.text).reduce((a, b) -> a + b).get());
            } else {
                String text = chunkList.size() == 1 ? chunkList.get(0).text : "";
                value = convertSimpleValues(typeMapping.type, text);
            }
            this.siblings.peek()
                         .add(new AbstractMap.SimpleEntry<>(jsonName,
                                                            ValueCreator.createKeyFieldEntry(jsonName, value)));

            return Optional.empty();
        }

        // Attempt 2:
        // If type is a record, then we need to create a map and add the values to it.
        while (!chunks.empty()) {
            XmlChunk chunk = chunks.pop();
            // All other nodes are ignored.
            if (chunk == startElement) {
                break;
            }
        }
        startElement.attributesMap.forEach((key, value) -> {
            BString fieldName = StringUtils.fromString(key);
            BString fieldValue = StringUtils.fromString(value);
            // TODO : Change key from Annotation
            values.add(
                    new AbstractMap.SimpleEntry<>(fieldName, ValueCreator.createKeyFieldEntry(fieldName, fieldValue)));
        });

        // Validate given record is mapping to the given xml value.
        // Check all required fields are present. Otherwise, it is an error.
        if (typeMapping.type.getTag() == TypeTags.RECORD_TYPE_TAG) {
            RecordType recordType = (RecordType) typeMapping.type;
            List<String> missingMappings = new ArrayList<>();
            for (Field field : recordType.getFields().values()) {
                long masks = field.getFlags();
                if (SymbolFlags.isFlagOn(masks, SymbolFlags.REQUIRED)) {
                    if (values.stream()
                              .map(Map.Entry::getKey)
                              .noneMatch(key -> key.getValue().equals(field.getFieldName()))) {
                        missingMappings.add(field.getFieldName());
                    }
                    // Change the type if needed.
                }
            }
            if (!missingMappings.isEmpty()) {
                return Optional.of(createTypeMappingError(typeMapping.type, "Missing required fields: " +
                        missingMappings.stream().collect(Collectors.joining(", "))));
            }

            BMapInitialValueEntry[] keyValues =
                    values.stream().map(Map.Entry::getValue).toArray(BMapInitialValueEntry[]::new);
            BMap<BString, Object> recordValue = ValueCreator.createRecordValue(recordType, keyValues);
            this.siblings.peek()
                         .add(new AbstractMap.SimpleEntry<>(jsonName,
                                                            ValueCreator.createKeyFieldEntry(jsonName, recordValue)));

        }

        // Mapping Value
        if (typeMapping.type.getTag() == TypeTags.MAP_TAG) {
            MapType mapType = (MapType) typeMapping.type;
            BMapInitialValueEntry[] keyValues =
                    values.stream().map(Map.Entry::getValue).toArray(BMapInitialValueEntry[]::new);
            BMap<BString, Object> mapValue = ValueCreator.createMapValue(mapType, keyValues);
            this.siblings.peek()
                         .add(new AbstractMap.SimpleEntry<>(jsonName,
                                                            ValueCreator.createKeyFieldEntry(jsonName, mapValue)));
        }
        // TODO : Handle other types
        return Optional.empty();
    }

    private void handleXmlText(XMLStreamReader xmlStreamReader) {
        final String text = xmlStreamReader.getText();

        XmlChunk.XmlText xmlText = new XmlChunk.XmlText(text);
        chunks.push(xmlText);
    }

    private void handleXmlComment(XMLStreamReader xmlStreamReader) {
        chunks.push(new XmlChunk.XmlComment(xmlStreamReader.getText()));
    }

    private void handleXmlPI(XMLStreamReader xmlStreamReader) {
        chunks.push(new XmlChunk.XmlProcessingInstruction(xmlStreamReader.getPITarget(), xmlStreamReader.getPIData()));
    }

    private boolean isRoot() {
        return this.types.empty();
    }

    private Optional<BError> checkRootType(String localPart) {
        Type describingType = this.typedesc.getDescribingType();
        if (describingType.getTag() == TypeTags.MAP_TAG || describingType.getTag() == TypeTags.RECORD_TYPE_TAG) {
            // Root node's JSON name doesn't matter.
            // No need to read the type annotation here, since this is initiating from the XML root. But fields,
            // field annotations are needed.
            TypeMapping typeMapping =
                    new TypeMapping(this.typedesc.getDescribingType(), StringUtils.fromString(localPart),
                                    StringUtils.fromString(ROOT));
            this.types.push(typeMapping);
            return Optional.empty();
        }
        return Optional.of(ErrorCreator.createError(
                StringUtils.fromString("Unexpected type: '" + describingType.getName() + "' at /")));
    }

    private Optional<BError> getNextType(String localPart) {
        TypeMapping typeMapping = types.peek();
        Optional<TypeMapping> nextType = getNextMemberType(typeMapping.type, localPart);
        if (nextType.isPresent()) {
            types.push(nextType.get());
            return Optional.empty();
        }
        return Optional.of(createTypeMappingError(typeMapping.type));
    }

    private Optional<TypeMapping> getNextMemberType(Type currentType, String localPart) {
        final int typeTag = currentType.getTag();
        TypeMapping nextTypeMapping;
        switch (typeTag) {
            case TypeTags.JSON_TAG:
            case TypeTags.ANYDATA_TAG:
            case TypeTags.ANY_TAG:
                // jsonName is same as xml name this case.
                // TODO : Check attribute name matters here.
                nextTypeMapping = new TypeMapping(currentType, StringUtils.fromString(localPart));
                return Optional.of(nextTypeMapping);
            case TypeTags.UNION_TAG:
                for (Type memberType : ((UnionType) currentType).getMemberTypes()) {
                    Optional<TypeMapping> result = getNextMemberType(memberType, localPart);
                    if (result.isPresent()) {
                        return result;  // Take First match
                    }
                }
                break;
            case TypeTags.MAP_TAG:
                // This will make sure, we will reuse the member type, for next iteration.
                nextTypeMapping = new TypeMapping(((MapType) currentType).getConstrainedType(),
                                                  StringUtils.fromString(localPart));
                return Optional.of(nextTypeMapping);
            case TypeTags.RECORD_TYPE_TAG:
                RecordType recordType = (RecordType) currentType;
                // TODO : Handle Annotation. First check for annotation, then check for field.
                // Once we read the annotation, we can set the jsonName(the fieldName) from the annotation attachment.
                // For now, we are using the localPart as the jsonName.
                String jsonName = localPart;
                if (recordType.getFields().containsKey(localPart)) {
                    nextTypeMapping = new TypeMapping(recordType.getFields().get(localPart).getFieldType(),
                                                      StringUtils.fromString(localPart),
                                                      StringUtils.fromString(jsonName));
                    return Optional.of(nextTypeMapping);
                } else if (!recordType.isSealed()) {
                    nextTypeMapping = new TypeMapping(recordType.getRestFieldType(), null);
                    return Optional.of(nextTypeMapping);
                } else {
                    return Optional.empty();
                }
            case TypeTags.ARRAY_TAG:
                ArrayType arrayType = (ArrayType) currentType;
                nextTypeMapping = new TypeMapping(arrayType.getElementType(), StringUtils.fromString(localPart));
                return Optional.of(nextTypeMapping);
            default:
                return Optional.empty();
        }
        return Optional.empty();
    }

    private BError createTypeMappingError(Type type) {
        return createTypeMappingError(type, "");
    }

    private BError createTypeMappingError(Type type, String reason) {
        StringBuilder pathBuilder = new StringBuilder();
        for (XmlChunk.XmlStartElement startElement : startElements) {
            pathBuilder.append("/").append(startElement.qName);
        }
        String path = pathBuilder.toString();
        if (path.isEmpty()) {
            path = "/";
        }

        StringBuilder msgBuilder = new StringBuilder();
        msgBuilder.append("Unexpected type mapping '");
        if (type.getName().isEmpty()) {
            msgBuilder.append(type);
        } else {
            msgBuilder.append(type.getQualifiedName());
        }
        msgBuilder.append("' at XML path '").append(path).append("'");
        if (!reason.isEmpty()) {
            msgBuilder.append(", ").append(reason);
        }
        return ErrorCreator.createError(StringUtils.fromString(msgBuilder.toString()));
    }

    /**
     * Represents XML to JSON mapping attributes based on OpenAPI Specification.
     */
    private static class TypeMapping {

        Type type;
        BString name;
        BString jsonName;
        boolean attribute = false;

        // Note : attribute = false means, this is a text node.
        // This is required to over-come the limitation with XML element with attributes AND content.
        // e.g. <element attr1="value1">content</element>
        // record { string element; @xmldata:Mapping{ } string attr1; }
        // See this issue. https://github.com/OAI/OpenAPI-Specification/issues/630

        TypeMapping(Type type, BString name) {
            this.type = type;
            this.name = name;
            this.jsonName = name;
        }

        TypeMapping(Type type, BString name, BString jsonName) {
            this.type = type;
            this.name = name;
            this.jsonName = jsonName;
        }
    }

    private static Object convertSimpleValues(Type type, String text) {
        switch (type.getTag()) {
            case TypeTags.INT_TAG:
                return fromString(StringUtils.fromString(text));
            case TypeTags.BOOLEAN_TAG:
                return org.ballerinalang.langlib.bool.FromString.fromString(StringUtils.fromString(text));
            case TypeTags.DECIMAL_TAG:
                return org.ballerinalang.langlib.decimal.FromString.fromString(StringUtils.fromString(text));
            case TypeTags.FLOAT_TAG:
                return org.ballerinalang.langlib.floatingpoint.FromString.fromString(StringUtils.fromString(text));
        }
        return StringUtils.fromString(text);
    }

    private static boolean containStringType(Type type) {
        switch (type.getTag()) {
            case TypeTags.STRING_TAG:
            case TypeTags.ANYDATA_TAG:
            case TypeTags.ANY_TAG:
            case TypeTags.READONLY_TAG:
            case TypeTags.JSON_TAG:
                return true;
            case TypeTags.UNION_TAG:
                for (Type memberType : ((UnionType) type).getMemberTypes()) {
                    if (containStringType(memberType)) {
                        return true;
                    }
                }
                return false;
            default:
                return false;
        }
    }

}
