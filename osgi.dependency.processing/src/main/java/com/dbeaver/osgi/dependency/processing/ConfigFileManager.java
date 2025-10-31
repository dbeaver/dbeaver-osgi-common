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

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

public class ConfigFileManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigFileManager.class);

    private static final String CONFIG_FILE_NAME = "config.properties";

    private static final Gson GSON = new GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create();

    public static @NotNull Properties readSettingsFile(Path configFilePath) throws IOException {
        if (!Files.exists(configFilePath)) {
            throw new IOException("Config file '" + configFilePath.toAbsolutePath() + "' not found");
        }
        try (BufferedReader reader = Files.newBufferedReader(configFilePath)) {
            var result = new Properties();
            result.load(reader);
            return result;
        }
    }

    /**
     * Read JSON file containing additional environment properties
     *  format - array of objects with "name" and "value" fields
     *  id - property name
     *  properies - property values
     *  Example:
     *  [
     *      {"uid": "config_uid", "type": "cli", "properties": {"password": "mypassword"}},
     * @param envPath
     * @return
     */
    @Nullable
    public static List<PropertyConfig> processAdditionalProperties(Path envPath) {
        if (!Files.exists(envPath)) {
            throw null;
        }
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(envPath))) {
            return GSON.fromJson(reader, new TypeToken<List<PropertyConfig>>() {}.getType());
        } catch (IOException | JsonParseException e) {
            log.error("Error reading JSON file: " + envPath.toAbsolutePath(), e);
            return null;
        }
    }

}
