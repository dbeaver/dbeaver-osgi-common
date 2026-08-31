/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2026 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dbeaver.osgi.dependency.processing.xml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchArgumentsXMLReaderExtensionTest {
    @TempDir
    Path parametersDirectory;

    @Test
    void expandsParameterSetsInMarkerOrder() throws Exception {
        Files.writeString(parametersDirectory.resolve("first.ini"), "-Done=1\n--add-opens=first");
        Files.writeString(parametersDirectory.resolve("second.ini"), "# ignored\n-Dtwo=2\n");
        XMLEventReader reader = reader("""
            <vmArgs>-Xms64m<!-- dbeaver-launch-parameters: first, second -->-Xmx1g</vmArgs>
            """);
        reader.nextEvent();

        assertArrayEquals(
            new String[]{"-Xms64m", "-Done=1", "--add-opens=first", "-Dtwo=2", "-Xmx1g"},
            LaunchArgumentsXMLReaderExtension.extractArgs(reader, "vmArgs", parametersDirectory)
        );
    }

    @Test
    void ignoresUnrelatedComments() throws Exception {
        XMLEventReader reader = reader("<vmArgs>-Xms64m<!-- explanation -->-Xmx1g</vmArgs>");
        reader.nextEvent();

        assertArrayEquals(
            new String[]{"-Xms64m", "-Xmx1g"},
            LaunchArgumentsXMLReaderExtension.extractArgs(reader, "vmArgs", parametersDirectory)
        );
    }

    @Test
    void rejectsUnknownParameterSet() throws Exception {
        XMLEventReader reader = reader("<vmArgs><!-- dbeaver-launch-parameters: missing --></vmArgs>");
        reader.nextEvent();

        Exception exception = assertThrows(
            Exception.class,
            () -> LaunchArgumentsXMLReaderExtension.extractArgs(reader, "vmArgs", parametersDirectory)
        );
        assertTrue(exception.getMessage().contains("Unknown launch parameter set 'missing'"));
    }

    private static XMLEventReader reader(String xml) throws Exception {
        return XMLInputFactory.newFactory().createXMLEventReader(new StringReader(xml));
    }
}
