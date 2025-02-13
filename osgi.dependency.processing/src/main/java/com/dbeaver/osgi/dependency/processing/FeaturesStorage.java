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
package com.dbeaver.osgi.dependency.processing;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class FeaturesStorage {

    public static final Logger log = LoggerFactory.getLogger(FeaturesStorage.class);

    private static final String FEATURES_XML_FILENAME = "feature.xml";

    private final Map<String, File> xmlFilesByFeaturesNames = new LinkedHashMap<>();

    public void importData(@Nonnull Collection<Path> featuresPaths) {
        for (var featuresFolderPath : featuresPaths) {
            var folders = featuresFolderPath.toFile().listFiles(File::isDirectory);
            if (folders == null) {
                log.warn("No folders was found in '{}'", featuresFolderPath);
                return;
            }
            for (var folder : folders) {
                var featureXmlFiles = folder.listFiles((dir, fileName) -> fileName.equals(FEATURES_XML_FILENAME));
                if (featureXmlFiles == null || featureXmlFiles.length == 0) {
                    log.warn("'{}' is not found in '{}'", FEATURES_XML_FILENAME, folder.getPath());
                    return;
                } else if (featureXmlFiles.length > 1) {
                    log.warn("Multiple '{}' was found in '{}'", FEATURES_XML_FILENAME, folder.getPath());
                }
                xmlFilesByFeaturesNames.put(folder.getName(), featureXmlFiles[0]);
            }
        }
    }

    public @Nullable File getFeatureXml(@Nonnull String featureName) {
        return xmlFilesByFeaturesNames.get(featureName);
    }
}
