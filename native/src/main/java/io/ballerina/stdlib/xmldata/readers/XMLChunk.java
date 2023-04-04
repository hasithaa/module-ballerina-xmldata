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

import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;

/**
 * TODO: Fix me.
 *
 * @since 2.0.0
 */
public class XMLChunk {

    final XMLChunkKind kind;

    private XMLChunk(XMLChunkKind kind) {
        this.kind = kind;
    }

    public XMLChunkKind getKind() {
        return kind;
    }

    public static class XMLStartElement extends XMLChunk {

        QName qName;
        Map<String, String> attributesMap = new HashMap<>();

        public XMLStartElement(QName elemName) {
            super(XMLChunkKind.START_ELEMENT);
            this.qName = elemName;
        }

        public void addAttribute(String key, String attrValue) {
            attributesMap.put(key, attrValue);
        }

        public void addAttributes(Map<String, String> attributesMap) {
            this.attributesMap.putAll(attributesMap);
        }

    }

    public static class XMLEndElement extends XMLChunk {

        QName qName;

        public XMLEndElement(QName qName) {
            super(XMLChunkKind.END_ELEMENT);
            this.qName = qName;
        }
    }

    public static class XMLEndDocument extends XMLChunk {

        public XMLEndDocument() {
            super(XMLChunkKind.END_DOCUMENT);
        }
    }

    public static class XMLComment extends XMLChunk {

        private final String comment;

        public XMLComment(String comment) {
            super(XMLChunkKind.COMMENT);
            this.comment = comment;
        }
    }

    public static class XMLText extends XMLChunk {

        private final String text;
        private final boolean possibleWhitespace;

        public XMLText(String text) {
            super(XMLChunkKind.TEXT);
            this.text = text;
            this.possibleWhitespace = text.isBlank();
        }
    }

    public static class XMLProcessingInstruction extends XMLChunk {


        private final String piTarget;
        private final String piData;

        public XMLProcessingInstruction(String piTarget, String piData) {
            super(XMLChunkKind.PROCESSING_INSTRUCTION);
            this.piTarget = piTarget;
            this.piData = piData;
        }
    }
}
