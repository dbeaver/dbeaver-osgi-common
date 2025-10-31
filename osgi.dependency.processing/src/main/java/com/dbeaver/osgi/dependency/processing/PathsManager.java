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

import com.dbeaver.osgi.dependency.processing.util.FileUtils;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum PathsManager {
    INSTANCE();

    private Collection<Path> featuresPaths;
    private Collection<Path> bundlesPaths;
    private Map<Path, String> productsPathsAndWorkDirs;
    private Collection<Path> testBundlesPaths;


    private Path eclipsePath;
    private Path eclipsePluginsPath;
    private Path eclipseFeaturesPath;
    private Set<Path> modulesRoots;
    private List<Path> additionalLibraries;
    private Path imlModules;
    private List<Path> additionalIMlModules;
    private Set<Path> mavenModules;
    private List<Path> ideaConfigurationFiles;
    private Path projectsFolderPath;
    private String workspaceName;
    private List<Path> additionalRepositoriesPaths;
    private Set<String> testLibraries;
    private Set<Path> excludePaths;
    private Path mavenRepoPath;

    private Map<String, Path> overrideData = new HashMap<>();

    private Map<String, Set<String>> associatedProperties;
    private Map<String, Set<String>> associatedEnvProperties;
    /**
     * Key - run configuration products uuid, value - list of names of other idea configs to run before launching configuration
     */
    private Map<String, Set<String>> runBeforeConfigs;

    private Map<String, Map<String, String>> propertyValueMap = new LinkedHashMap<>();
    private Map<String, Map<String, String>> envPropertyValueMap = new LinkedHashMap<>();

    public void init(
        @NotNull Properties settings,
        @NotNull Path projectsFolderPath,
        @Nullable Path eclipsePath,
        @NotNull Path... additionalBundlesPaths
    ) throws IOException {
        if (eclipsePath == null) {
            eclipsePath = projectsFolderPath.resolve(ConfigurationConstants.DEFAULT_WORKSPACE_LOCATION);
        }
        this.mavenRepoPath = Paths.get(System.getProperty("maven.repo.local", System.getProperty("user.home") + "/.m2/repository"));

        this.eclipsePath = eclipsePath;
        eclipsePluginsPath = eclipsePath.resolve(ConfigurationConstants.PLUGINS_FOLDER);

        if (!eclipsePluginsPath.toFile().exists()) {
            Files.createDirectories(eclipsePluginsPath);
        }
        this.workspaceName = (String) settings.get(ConfigurationConstants.WORKSPACE_NAME_PARAM);

        imlModules = eclipsePath.getParent().resolve(workspaceName);
        if (!imlModules.toFile().exists()) {
            Files.createDirectories(imlModules);
        }

        eclipseFeaturesPath = eclipsePath.resolve(ConfigurationConstants.FEATURES_FOLDER);
        if (!eclipseFeaturesPath.toFile().exists()) {
            Files.createDirectories(eclipseFeaturesPath);
        }
        var featuresPathsString = (String) settings.get(ConfigurationConstants.FEATURES_PATHS_PARAM);
        featuresPaths = Arrays.stream(featuresPathsString.split(";"))
            .map(String::trim)
            .map(projectsFolderPath::resolve)
            .filter(FileUtils::exists)
            .collect(Collectors.toList());
        featuresPaths.add(eclipseFeaturesPath);

        var additionalRepositoriesPathsStrings = (String) settings.get(ConfigurationConstants.OPTIONAL_FEATURE_REPOSITORIES_PARAM);
        if (additionalRepositoriesPathsStrings != null) {
            additionalRepositoriesPaths = Arrays.stream(additionalRepositoriesPathsStrings.split(";"))
                .map(String::trim)
                .map(projectsFolderPath::resolve)
                .filter(FileUtils::exists)
                .collect(Collectors.toList());
        } else {
            additionalRepositoriesPaths = List.of();
        }

        String additionalIMlModulesString = (String) settings.get(ConfigurationConstants.ADDITIONAL_IML_MODULES_PARAM);
        if (additionalIMlModulesString != null) {
            additionalIMlModules = Arrays.stream(additionalIMlModulesString.split(";"))
                .map(String::trim)
                .map(projectsFolderPath::resolve)
                .filter(FileUtils::exists)
                .collect(Collectors.toList());
        }
        String mavenModulesString = (String) settings.get(ConfigurationConstants.MAVEN_MODULES);
        if (mavenModulesString != null) {
            mavenModules = Arrays.stream(mavenModulesString.split(";"))
                .map(String::trim)
                .map(projectsFolderPath::resolve)
                .filter(FileUtils::exists)
                .collect(Collectors.toSet());
        }
        String overrideDataString = (String) settings.getOrDefault(ConfigurationConstants.OVERRIDE_DATA_FOLDER, "");
        if (overrideDataString != null) {
            for (String pathString : overrideDataString.split(";")) {String trim = pathString.trim();
                if (trim.contains(":")) {
                    String[] pathAndWorkDir = trim.split(":");
                    if (pathAndWorkDir.length != 2) {
                        continue;
                    }
                    Path productPath = projectsFolderPath.resolve(pathAndWorkDir[1]);
                        overrideData.put(pathAndWorkDir[0], productPath);
                }
            }
        }

        var bundlesPathsString = (String) settings.get(ConfigurationConstants.BUNDLES_PATHS_PARAM);
        bundlesPaths = Stream.concat(
                Arrays.stream(bundlesPathsString.split(";"))
                    .map(String::trim)
                    .map(projectsFolderPath::resolve),
                Stream.concat(
                    Stream.of(eclipsePluginsPath),
                    Arrays.stream(additionalBundlesPaths)
                )
            )
            .filter(FileUtils::exists)
            .collect(Collectors.toList());
        var productsPathsString = (String) settings.get(ConfigurationConstants.PRODUCTS_PATHS_PARAM);
        productsPathsAndWorkDirs = resolveRootPaths(projectsFolderPath, productsPathsString);
        Stream<Path> allModules = Stream.concat(Arrays.stream(bundlesPathsString.split(";"))
            .map(Path::of), Arrays.stream(featuresPathsString.split(";")).map(Path::of));
        Set<Path> collect = allModules.collect(Collectors.toSet());
        Set<Path> set = new HashSet<>();
        for (Path path : collect) {
            Path root = path;
            while (root.getParent() != null) {
                root = root.getParent();
            }
            Path resolvedRoot = projectsFolderPath.resolve(root);
            if (FileUtils.exists(resolvedRoot)) {
                set.add(resolvedRoot);
            }
        }
        modulesRoots = set;
        String additionalModuleRootsString = (String) settings.get(ConfigurationConstants.ADDITIONAL_MODULE_ROOTS_PARAM);
        if (additionalModuleRootsString != null) {
            Set<Path> additionalModuleRoots = Arrays.stream(additionalModuleRootsString.split(";"))
                .map(String::trim)
                .map(projectsFolderPath::resolve).collect(Collectors.toSet());
            modulesRoots.addAll(additionalModuleRoots);
        }
        excludePaths = new LinkedHashSet<>();
        String excludePathsString = (String) settings.get(ConfigurationConstants.EXCLUDED_OUTPUT_PARAM);
        if (excludePathsString != null) {
            Set<Path> excludes = Arrays.stream(excludePathsString.split(";"))
                .map(String::trim)
                .map(projectsFolderPath::resolve).collect(Collectors.toSet());
            excludePaths.addAll(excludes);
        }
        associatedProperties = extractAssociatedProperties(settings, ConfigurationConstants.ASSOCIATED_VM_PROPERTIES, propertyValueMap);
        associatedEnvProperties = extractAssociatedProperties(settings, ConfigurationConstants.ASSOCIATED_ENV_PROPERTIES,
            envPropertyValueMap
        );

        var testBundlesPathsString = (String) settings.getOrDefault(ConfigurationConstants.TEST_BUNDLE_PATHS_PARAM, "");
        testBundlesPaths =
            Arrays.stream(testBundlesPathsString.split(";"))
                .map(String::trim)
                .map(projectsFolderPath::resolve)
                .filter(FileUtils::exists)
                .collect(Collectors.toList());
        var testLibrariesString = (String) settings.get(ConfigurationConstants.TEST_LIBRARIES);
        if (testLibrariesString != null) {
            this.testLibraries = Arrays.stream(testLibrariesString.split(";"))
                .map(String::trim).collect(Collectors.toSet());
        }
        var additionalLibrariesString = (String) settings.get(ConfigurationConstants.ADDITIONAL_LIBRARIES_PATHS_PARAM);
        if (additionalLibrariesString != null) {
            this.additionalLibraries = Arrays.stream(additionalLibrariesString.split(";"))
                .map(String::trim)
                .map(projectsFolderPath::resolve)
                .filter(FileUtils::exists)
                .collect(Collectors.toList());;
        }
        String ideaConfigurationFilesString = (String) settings.get(ConfigurationConstants.IDEA_CONFIGURATION_FILES_PATHS_PARAM);
        if (ideaConfigurationFilesString != null) {
            this.ideaConfigurationFiles = Arrays.stream(ideaConfigurationFilesString.split(";"))
                .map(String::trim)
                .map(projectsFolderPath::resolve)
                .filter(FileUtils::exists)
                .collect(Collectors.toList());
        }
        this.projectsFolderPath = projectsFolderPath;
        String runBeforeScriptsString = (String) settings.get(ConfigurationConstants.RUN_BEFORE_SCRIPTS);
        if (runBeforeScriptsString != null) {
            // uuid:path - Adds launch script for run configuration with uuid
            runBeforeConfigs = Arrays.stream(runBeforeScriptsString.split(";"))
                .map(String::trim)
                .map(s -> s.split("="))
                .filter(arr -> arr.length == 2)
                .collect(Collectors.toMap(arr -> arr[0], arr -> Set.of(arr[1].split(","))));
        }
    }

    public void associateAdditionalProperties(List<PropertyConfig> additionalProperties) {
        for (PropertyConfig additionalProperty : additionalProperties) {
            for (String uid : additionalProperty.uids()) {
                if (additionalProperty.type() == PropertyConfig.Type.CLI) {
                    associatedProperties.computeIfAbsent(uid, k -> new LinkedHashSet<>()).add(additionalProperty.name());
                    propertyValueMap.put(additionalProperty.name(), additionalProperty.properties());
                } else if (additionalProperty.type() == PropertyConfig.Type.ENV) {
                    associatedEnvProperties.computeIfAbsent(uid, k -> new LinkedHashSet<>()).add(additionalProperty.name());
                    envPropertyValueMap.put(additionalProperty.name(), additionalProperty.properties());
                }
            }

        }
    }

    private Map<String, Set<String>> extractAssociatedProperties(
        @NotNull Properties settings,
        @NotNull String propertyType,
        Map<String, Map<String, String>> associatedProperties
    ) {
        Object associatedPropertiesObject = settings.get(propertyType);
        if (associatedPropertiesObject instanceof String properties) {
            Stream<String> propertyStream = Arrays.stream(properties.split(";"))
                .filter(it -> it.split("=").length == 2).map(String::trim);
            return propertyStream.peek(productProperties -> {
                String values = productProperties.split("=")[1];
                Set<String> valuesSet = getSet(values);
                for (String s : valuesSet) {
                    associatedProperties.computeIfAbsent(s, prop -> loadNewProperty(prop, settings));
                }
            }).collect(Collectors.toMap(it -> it.split("=")[0], it -> getSet(it.split("=")[1])));
        }
        return null;
    }

    @NotNull
    private static Map<Path, String> resolveRootPaths(@NotNull Path projectsFolderPath, String productsPathsString) {
        Map<Path, String> list = new LinkedHashMap<>();
        for (String pathString : productsPathsString.split(";")) {
            String trim = pathString.trim();
            if (pathString.contains(":")) {
                String[] pathAndWorkDir = pathString.split(":");
                if (pathAndWorkDir.length != 2) {
                    continue;
                }
                Path productPath = projectsFolderPath.resolve(pathAndWorkDir[0]);
                if (FileUtils.exists(productPath)) {
                    list.put(productPath, pathAndWorkDir[1]);
                }
            } else {
                Path  resolve = projectsFolderPath.resolve(trim);
                if (FileUtils.exists(resolve)) {
                    list.put(resolve, null);
                }
            }
        }
        return list;
    }

    public @NotNull Collection<Path> getFeaturesLocations() {
        return featuresPaths;
    }

    public @NotNull Collection<Path> getModulesRoots() {
        return modulesRoots;
    }


    @Nullable
    public Collection<Path> getMavenModules() {
        return mavenModules;
    }

    public @NotNull Collection<Path> getBundlesLocations() {
        return bundlesPaths;
    }

    public @NotNull Collection<Path> getTestBundlesPaths() {
        return testBundlesPaths;
    }

    public @NotNull Path getEclipsePath() {
        return eclipsePath;
    }

    public @NotNull Path getTreeOutputFolder() {
        return eclipsePath.getParent().resolve(ConfigurationConstants.TREE_OUTPUT);
    }

    public @NotNull Path getEclipsePluginsPath() {
        return eclipsePluginsPath;
    }

    public Map<Path, String> getProductsPathsAndWorkDirs() {
        return productsPathsAndWorkDirs;
    }

    public @Nullable List<Path> getAdditionalLibraries() {
        return additionalLibraries;
    }

    public @Nullable Set<String> getTestLibraries() {
        return testLibraries;
    }

    @Nullable
    public List<Path> getAdditionalIMlModules() {
        return additionalIMlModules;
    }

    @NotNull
    public Path getEclipseFeaturesPath() {
        return eclipseFeaturesPath;
    }

    @NotNull
    public Path getImlModulesPath() {
        return imlModules;
    }

    @Nullable
    public Set<String> getRunBeforeConfigs(String product) {
        return runBeforeConfigs.get(product);
    }

    @NotNull
    public Path getMavenRepoPath() {
        return mavenRepoPath;
    }

    @Nullable
    public  List<Path> getIdeaConfigurationFiles() {
        return ideaConfigurationFiles;
    }

    public List<Path> getAdditionalRepositoriesPaths() {
        return additionalRepositoriesPaths;
    }

    public Set<Path> getExcludePaths() {
        return excludePaths;
    }

    public Path getProjectsFolderPath() {
        return projectsFolderPath;
    }


    public Map<String, String> getAssociatedVMParameters(String product) {
        if (associatedProperties == null) {
            return null;
        }
        Set<String> properties = associatedProperties.get(product);
        if (properties != null) {
            Map<String, String> result = new HashMap<>();
            for (String property : properties) {
                Map<String, String> stringStringMap = propertyValueMap.get(property);
                result.putAll(stringStringMap);
            }
            return result;
        } else {
            return null;
        }
    }

    public Map<String, String> getAssociatedEnvParameters(String product) {
        if (associatedEnvProperties == null) {
            return null;
        }
        Set<String> properties = associatedEnvProperties.get(product);
        if (properties != null) {
            Map<String, String> result = new HashMap<>();
            for (String property : properties) {
                Map<String, String> stringStringMap = envPropertyValueMap.get(property);
                result.putAll(stringStringMap);
            }
            return result;
        } else {
            return null;
        }
    }

    @NotNull
    private static Set<String> getSet(String values) {
        Set<String> valuesSet = new LinkedHashSet<>();
        if (values.contains(",")) {
            valuesSet.addAll(List.of(values.split(",")));
        } else {
            valuesSet.add(values);
        }
        return valuesSet;
    }

    private Map<String, String> loadNewProperty(@NotNull String property, @NotNull Properties properties) {
        String propertyString = properties.getProperty(property);
        return Arrays.stream(propertyString.split(";"))
            .map(pair -> pair.split("=", 2))  // Split each key=value pair
            .filter(pair -> pair.length == 2) // Ensure valid key=value pairs
            .collect(Collectors.toMap(
                pair -> pair[0].trim(),        // Key
                pair -> pair[1].trim()         // Value
            ));

    }

    public Path getOverridenDataFolderLocation(String productId) {
        return overrideData.get(productId);
    }
}
