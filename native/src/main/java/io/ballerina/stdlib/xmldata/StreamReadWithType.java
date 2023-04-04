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
package io.ballerina.stdlib.xmldata;

import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.Future;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.types.MethodType;
import io.ballerina.runtime.api.types.ObjectType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BStream;
import io.ballerina.runtime.api.values.BTypedesc;
import io.ballerina.stdlib.xmldata.readers.ByteBlockReader;
import io.ballerina.stdlib.xmldata.readers.XMLTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Consumer;


/**
 * TODO: Fix me.
 *
 * @since 2.0.0
 */
public class StreamReadWithType {

    private final BStream stream;
    private final Environment env;
    private final Future future;

    public StreamReadWithType(Environment env, BStream stream, Future future) {
        this.stream = stream;
        this.env = env;
        this.future = future;
    }

    public static Object streamReadXMLWithType(Environment env, BStream stream, BTypedesc typedesc) {
        final Future future = env.markAsync();
        StreamReadWithType type = new StreamReadWithType(env, stream, future);
        final Optional<BError> bError = validateTypeDesc(typedesc);
        if (bError.isPresent()) {
            BError returnValue = bError.get();
            future.complete(returnValue);
            return returnValue;
        }
        type.transform(typedesc);
        return null;
    }


    private static Optional<BError> validateTypeDesc(BTypedesc typedesc) {
        final Type describingType = typedesc.getDescribingType();
        if (describingType.getTag() == TypeTags.RECORD_TYPE_TAG
                || describingType.getTag() == TypeTags.MAP_TAG) {
            return Optional.empty();
        }
        return Optional.of(ErrorCreator.createError(StringUtils.fromString("Invalid type descriptor. Expected record or map")));
    }

    public void transform(BTypedesc typedesc) {
        final BObject iteratorObj = stream.getIteratorObj();
        try (ByteBlockReader byteBlockSteam = new ByteBlockReader(env, iteratorObj, resolveNextMethod(iteratorObj), this.future)) {
            Transformer<InputStream> transformer = new Transformer<>(future, typedesc);
            byteBlockSteam.readAllBlocksAncConsumer(transformer);
        } catch (IOException e) {
            throw new RuntimeException("");
        }
    }

    private MethodType resolveNextMethod(BObject iterator) {
        ObjectType objectType = (ObjectType) TypeUtils.getReferredType(iterator.getType());
        MethodType[] methods = objectType.getMethods();
        // Assumes compile-time validation of the iterator object
        for (MethodType method : methods) {
            if (method.getName().equals("next")) {
                return method;
            }
        }
        throw new IllegalStateException("next method not found in the iterator object");
    }

    static class Transformer<T extends InputStream> implements Consumer<T> {

        private final Future future;
        private final BTypedesc typedesc;

        public Transformer(Future future, BTypedesc typedesc) {
            this.future = future;
            this.typedesc = typedesc;
        }

        @Override
        public void accept(InputStream inputStream) {
            XMLTransformer xmlTransformer = new XMLTransformer(inputStream);
            try {
                xmlTransformer.readAndTransform(typedesc);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            System.out.println(inputStream);
        }
    }

}
