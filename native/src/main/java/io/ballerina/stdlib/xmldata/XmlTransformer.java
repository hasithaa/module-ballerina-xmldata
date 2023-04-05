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
import io.ballerina.runtime.api.values.*;
import io.ballerina.stdlib.xmldata.readers.ByteBlockReader;
import io.ballerina.stdlib.xmldata.readers.XmlTreeBuilder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.function.Consumer;


/**
 * TODO: Fix me.
 *
 * @since 2.0.0
 */
public class XmlTransformer {

    private final Environment env;
    private final Object source;

    XmlTransformer(Environment env, Object source) {
        this.env = env;
        this.source = source;
    }

    public static Object streamReadXMLWithType(Environment env, Object source, BTypedesc typedesc) {
        XmlTransformer transformer = new XmlTransformer(env, source);
        final Object typeResult = transformer.constructDescribingType(source, typedesc);
        // TODO : This is just a validation. No need to check the instance.
        if (typeResult instanceof BError) {
            BError returnValue = (BError) typeResult;
            return returnValue;
        } else if (typeResult instanceof BMap) {
            BMap returnValue = (BMap) typeResult;
            return returnValue;
        }
        return typeResult;
    }


    private Object constructDescribingType(Object source, BTypedesc typedesc) {

        final Type describingType = typedesc.getDescribingType();

        // Supports only map, record. Array and JSON is not supported from the signature it self.
        if (describingType.getTag() != TypeTags.RECORD_TYPE_TAG
                && describingType.getTag() != TypeTags.MAP_TAG) {
            return ErrorCreator.createError(
                    StringUtils.fromString("Invalid type descriptor. Expected record or map"));
        }

        if (source instanceof BArray) {
            return transformFromByteArray((BArray) source, typedesc);
        } else if (source instanceof BStream) {
            return transformFromByteStream((BStream) source, typedesc);
        }
        return null;
    }

    public Object transformFromByteArray(BArray value, BTypedesc typedesc) {
        InputStream inputStream = new ByteArrayInputStream(value.getBytes());
        XmlTreeBuilder xmlTreeBuilder = new XmlTreeBuilder(inputStream);
        return xmlTreeBuilder.readAndTransform(typedesc);
    }

    public Object transformFromByteStream(BStream stream, BTypedesc typedesc) {
        final BObject iteratorObj = stream.getIteratorObj();
        Future future = env.markAsync();
        try (ByteBlockReader byteBlockSteam =
                     new ByteBlockReader(env, iteratorObj, resolveNextMethod(iteratorObj))) {
            Transformer<Object> transformer = new Transformer<>(future, typedesc);
            byteBlockSteam.readAllBlocksAncConsumer(transformer);
        } catch (Exception e) {
            return ErrorCreator.createError(
                    StringUtils.fromString("Error occurred while reading the stream: " + e.getMessage()));
        }
        return null;
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

    static class Transformer<T> implements Consumer<T> {

        private final Future future;
        private final BTypedesc typedesc;

        public Transformer(Future future, BTypedesc typedesc) {
            this.future = future;
            this.typedesc = typedesc;
        }

        @Override
        public void accept(Object value) {
            if (value instanceof BError) {
                future.complete(value);
            } else if (value instanceof InputStream) {
                InputStream inputStream = (InputStream) value;
                XmlTreeBuilder xmlTreeBuilder = new XmlTreeBuilder(inputStream);
                Object result = xmlTreeBuilder.readAndTransform(typedesc);
                future.complete(result);
            }
        }
    }

}
