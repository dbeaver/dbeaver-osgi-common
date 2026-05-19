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

import com.dbeaver.osgi.dependency.processing.Result;
import com.dbeaver.osgi.dependency.processing.resolvers.FeatureResolver;
import com.dbeaver.osgi.dependency.processing.util.DependencyGraph;
import org.jkiss.code.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;

class FeatureXmlReaderExtension extends XmlReaderExtension {

    private static final Logger log = LoggerFactory.getLogger(FeatureXmlReaderExtension.class);

    static void resolveFeature(@NotNull Result result, @NotNull Attribute attribute, DependencyGraph graph) {
        try {
            FeatureResolver.resolveFeatureDependencies(result, attribute.getValue(), graph);
        } catch (IOException | XMLStreamException e) {
            log.error("Failed to resolve feature", e);
        }
    }

    @Override
    public void resolveStartElement(@NotNull Result result,
                                    @NotNull StartElement startElement,
                                    XMLEventReader reader,
                                    DependencyGraph graph) {
        var nameLocalPart = startElement.getName().getLocalPart();
        Attribute attribute = null;
        switch (nameLocalPart) {
            case "import": {
                attribute = startElement.getAttributeByName(FEATURE_ATTR_NAME);
                break;
            }
            case "includes", "feature": {
                attribute = startElement.getAttributeByName(ID_ATTR_NAME);
                break;
            }
        }
        if (attribute == null) {
            return;
        }
        resolveFeature(result, attribute, graph);
    }
}
