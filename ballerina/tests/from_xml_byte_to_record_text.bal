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
import ballerina/test;

@test:Config {
    groups: ["xml-bytearray"]
}
isolated function testXmlL1() returns error? {

    xml x1 = xml `<book><id>1</id> <title>SomeBook1</title> <author>Some Author1</author></book>`;
    xml x2 = xml `<book id="2"> <title>SomeBook2</title> <author>Some Author2</author></book>`;
    xml x3 = xml `<book id="3" title="SomeBook3"><author>Some Author3</author></book>`;
    xml x4 = xml `<book id="4" title="SomeBook4" author="Some Author4"></book>`;

    byte[] bytes = x1.toString().toBytes();
    BookL1 rx1 = check streamReadXmlWithType(bytes);
    test:assertEquals(rx1.id, 1);
    test:assertEquals(rx1.title, "SomeBook1");
    test:assertEquals(rx1.author, "Some Author1");

    bytes = x2.toString().toBytes();
    BookL1 rx2 = check streamReadXmlWithType(bytes);
    test:assertEquals(rx2.id, 2);
    test:assertEquals(rx2.title, "SomeBook2");
    test:assertEquals(rx2.author, "Some Author2");

    bytes = x3.toString().toBytes();
    BookL1 rx3 = check streamReadXmlWithType(bytes);
    test:assertEquals(rx3.id, 3);
    test:assertEquals(rx3.title, "SomeBook3");
    test:assertEquals(rx3.author, "Some Author3");

    bytes = x4.toString().toBytes();
    BookL1 rx4 = check streamReadXmlWithType(bytes);
    test:assertEquals(rx4.id, 4);
    test:assertEquals(rx4.title, "SomeBook4");
    test:assertEquals(rx4.author, "Some Author4");
}

type BookL1 record {
    string title;
    string author;
    int id;
};
