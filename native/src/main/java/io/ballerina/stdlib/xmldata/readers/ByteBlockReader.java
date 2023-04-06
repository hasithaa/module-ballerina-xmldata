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

import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.async.Callback;
import io.ballerina.runtime.api.async.StrandMetadata;
import io.ballerina.runtime.api.types.MethodType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.values.*;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * TODO : Fix me.
 */
public class ByteBlockReader implements Closeable {

    private final BObject iterator;
    private final Environment env;
    private final String nextMethodName;
    private final Type returnType;
    private final String strandName;
    private final StrandMetadata metadata;
    private final Map<String, Object> properties;

    List<byte[]> chunks = new ArrayList<>();


    public ByteBlockReader(Environment env, BObject iterator, MethodType nextMethod) {
        this.env = env;
        this.iterator = iterator;
        this.nextMethodName = nextMethod.getName();
        this.returnType = nextMethod.getReturnType();
        this.strandName = env.getStrandName().orElse("");
        this.metadata = env.getStrandMetadata();
        this.properties = Map.of();
    }

    public void readAllBlocksAncConsumer(Consumer<Object> futureResultConsumer) throws IOException {
        if (iterator == null || nextMethodName == null || returnType == null) {
            throw new IOException("Invalid byte[] stream");
        }
        scheduleNextRead(futureResultConsumer);
    }

    private void scheduleNextRead(Consumer<Object> futureResultConsumer) {
        Callback callback = new Callback() {
            @Override
            public void notifySuccess(Object o) {
                if (o == null) {
                    futureResultConsumer.accept(new ByteBlockSteam(chunks));
                    return;
                }
                if (o instanceof BMap) {
                    BMap<BString, Object> valueRecord = (BMap<BString, Object>) o;
                    final BString value = Arrays.stream(valueRecord.getKeys()).findFirst().get();
                    final BArray arrayValue = valueRecord.getArrayValue(value);
                    chunks.add(arrayValue.getByteArray());
                }
                scheduleNextRead(futureResultConsumer);
            }

            @Override
            public void notifyFailure(BError bError) {
                futureResultConsumer.accept(bError);
            }
        };
        env.getRuntime().invokeMethodAsyncSequentially(iterator,
                                                       nextMethodName,
                                                       strandName,
                                                       metadata,
                                                       callback,
                                                       properties,
                                                       returnType);
    }

    @Override
    public void close() {
        this.chunks.clear();
        // TODO: Close the stream
    }
}
