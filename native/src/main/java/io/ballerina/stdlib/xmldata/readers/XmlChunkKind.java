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

/**
 * Represents XML Chunk
 *
 * @since 2.5.0
 */
public enum XmlChunkKind {

    /**
     * Represents XML Start Element.
     */
    START_ELEMENT,

    /**
     * Represents XML End Element.
     */
    END_ELEMENT,

    /**
     * Represents XML End Document.
     */
    END_DOCUMENT,

    /**
     * Represents XML Comment.
     */
    COMMENT,

    /**
     * Represents XML Text.
     */
    TEXT,

    /**
     * Represents XML Processing Instruction.
     */
    PROCESSING_INSTRUCTION,

    /**
     * Represents XML Element.
     */
    ELEMENT
}
