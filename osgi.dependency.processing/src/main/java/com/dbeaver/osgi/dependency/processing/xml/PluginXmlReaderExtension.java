/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
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

import com.dbeaver.osgi.dependency.processing.Result;
import com.dbeaver.osgi.dependency.processing.p2.P2RepositoryManager;
import jakarta.annotation.Nonnull;
import com.dbeaver.osgi.dependency.processing.resolvers.PluginResolver;
import com.dbeaver.osgi.dependency.processing.util.DependencyGraph;
import org.jkiss.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import java.io.IOException;

class PluginXmlReaderExtension extends XmlReaderExtension {

    private static final Logger log = LoggerFactory.getLogger(PluginXmlReaderExtension.class);

    static void resolvePlugin(
        @Nonnull Result result,
        @Nonnull StartElement startElement,
        @Nonnull Attribute idAttr,
        DependencyGraph graph
    ) {
        var startLevelAttr = startElement.getAttributeByName(START_LEVEL_ATTR_NAME);
        var startLevel = startLevelAttr != null
            ? Integer.parseInt(startLevelAttr.getValue())
            : null;
        try {
            PluginResolver.resolvePluginDependencies(
                result,
                new Pair<>(idAttr.getValue(), null),
                startLevel,
                P2RepositoryManager.INSTANCE.getLookupCache(),
                graph
            );
        } catch (IOException e) {
            log.error("Failed to resolve plugin", e);
        }
    }

    @Override
    public void resolveStartElement(
        @Nonnull Result result,
        @Nonnull StartElement startElement,
        XMLEventReader reader,
        DependencyGraph graph
    ) {
        if (!matchesDeclaredOS(startElement)) {
            return;
        }

        var nameLocalPart = startElement.getName().getLocalPart();
        Attribute attribute = null;
        switch (nameLocalPart) {
            case "plugin": {
                attribute = startElement.getAttributeByName(ID_ATTR_NAME);
                break;
            }
            case "import": {
                attribute = startElement.getAttributeByName(PLUGIN_ATTR_NAME);
                break;
            }
        }
        if (attribute == null) {
            return;
        }
        resolvePlugin(result, startElement, attribute, graph);
    }
}
