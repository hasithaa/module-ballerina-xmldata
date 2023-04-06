// Copyright (c) 2023 WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/io;
import ballerina/test;

@test:Config {
    groups: ["xmlio-stream"]
}
isolated function testStreamReadXML() returns error? {
    stream<byte[], error?> byteBlockStream = check io:fileReadBlocksAsStream("tests/resources/large-data.xml");
    record{} res2 = check streamReadXmlWithType(byteBlockStream);
    io:println(res2);
}

@test:Config {
    groups: ["xmlio"]
}
isolated function testByteArrayXML() returns error? {
    xml x = xml `<book id="0"> <title>SomeBook</title> <author>Some Author</author></book>`;
    byte[] bytes = x.toString().toBytes();
    Book res1 = check streamReadXmlWithType(bytes);
    io:println(res1);
}

type Book record {
    string title;
    string author;
    int id;
};