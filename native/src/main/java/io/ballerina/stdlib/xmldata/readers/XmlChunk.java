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
public class XmlChunk {

    final XmlChunkKind kind;

    private XmlChunk(XmlChunkKind kind) {
        this.kind = kind;
    }

    public XmlChunkKind getKind() {
        return kind;
    }

    public static class XmlStartElement extends XmlChunk {

        QName qName;
        Map<String, String> attributesMap = new HashMap<>();

        public XmlStartElement(QName elemName) {
            super(XmlChunkKind.START_ELEMENT);
            this.qName = elemName;
        }

        public void addAttribute(String key, String attrValue) {
            attributesMap.put(key, attrValue);
        }

        public void addAttributes(Map<String, String> attributesMap) {
            this.attributesMap.putAll(attributesMap);
        }

    }

    public static class XmlEndElement extends XmlChunk {

        QName qName;

        public XmlEndElement(QName qName) {
            super(XmlChunkKind.END_ELEMENT);
            this.qName = qName;
        }
    }

    public static class XmlEndDocument extends XmlChunk {

        public XmlEndDocument() {
            super(XmlChunkKind.END_DOCUMENT);
        }
    }

    public static class XmlComment extends XmlChunk {

        private final String comment;

        public XmlComment(String comment) {
            super(XmlChunkKind.COMMENT);
            this.comment = comment;
        }
    }

    public static class XmlText extends XmlChunk {

        private final String text;
        private final boolean possibleWhitespace;

        public XmlText(String text) {
            super(XmlChunkKind.TEXT);
            this.text = text;
            this.possibleWhitespace = text.isBlank();
        }
    }

    public static class XmlProcessingInstruction extends XmlChunk {


        private final String piTarget;
        private final String piData;

        public XmlProcessingInstruction(String piTarget, String piData) {
            super(XmlChunkKind.PROCESSING_INSTRUCTION);
            this.piTarget = piTarget;
            this.piData = piData;
        }
    }
}
