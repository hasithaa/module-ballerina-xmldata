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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * TODO: Fix me.
 *
 * @since 2.0.0
 */
public class ByteBlockSteam extends InputStream {


    private final List<byte[]> chunks;

    private byte[] currentChunk = new byte[0];
    private int nextChunkIndex = 0;

    ByteBlockSteam(List<byte[]> chunks) {
        this.chunks = chunks;
    }

    public int read() {
        if (hasBytesInCurrentChunk()) {;
            return currentChunk[nextChunkIndex++];
        }
        // Need to get a new block from the stream, before reading again.
        nextChunkIndex = 0;
        if (readNextChunk()) {
            return read();
        }
        return -1;
    }

    @Override
    public void close() throws IOException {
        super.close();
        chunks.clear();
    }

    private boolean readNextChunk() {
        if (chunks.size() == 0) {
            return false;
        }
        currentChunk = chunks.remove(0);
        return true;
    }

    private boolean hasBytesInCurrentChunk() {
        return currentChunk.length != 0 && nextChunkIndex < currentChunk.length;
    }
}
