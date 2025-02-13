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
package com.dbeaver.osgi.dependency.processing.resolvers;

import com.dbeaver.osgi.dependency.processing.*;
import com.dbeaver.osgi.dependency.processing.p2.P2BundleLookupCache;
import com.dbeaver.osgi.dependency.processing.p2.P2RepositoryManager;
import com.dbeaver.osgi.dependency.processing.p2.repository.RemoteP2BundleInfo;
import com.dbeaver.osgi.dependency.processing.util.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jkiss.code.NotNull;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class PluginResolver {
    private static final Logger log = LoggerFactory.getLogger(PluginResolver.class);

    private static final Path MANIFEST_PATH = Paths.get("META-INF", "MANIFEST.MF");

    private static final Map<String, String> correctedFolderNames = Map.of(
    );

    public static void resolvePluginDependencies(
        @Nonnull Result result,
        @Nonnull Pair<String, VersionRange> bundleInfo,
        @Nullable Integer startLevel,
        P2BundleLookupCache cache,
        DependencyGraph graph) throws IOException {
        if (PackageChecker.INSTANCE.isPackageExcluded(bundleInfo.getFirst())) {
            return;
        }

        FeatureInfo currentFeature = FeatureResolver.getCurrentFeature(result.getProductPath());

        var previousParsedBundle = result.getBundleByInfoAndVersion(bundleInfo);
        if (previousParsedBundle != null) {
            if (currentFeature != null) {
                currentFeature.addBundleDependency(previousParsedBundle);
            }
            graph.addCurrentNodeDependency(previousParsedBundle.getBundleName());
            if (previousParsedBundle.getStartLevel() == null && startLevel != null) {
                // if previousParsedBundle does not have 'startLevel' — update it
                var newParsedBundle = new BundleInfoBuilder().setPath(previousParsedBundle.getPath())
                    .setBundleName(previousParsedBundle.getBundleName())
                    .setBundleVersion(previousParsedBundle.getBundleVersion())
                    .setClasspathLibs(previousParsedBundle.getClasspathLibs())
                    .setRequireBundles(previousParsedBundle.getRequireBundles())
                    .setReexportedBundles(previousParsedBundle.getReexportedBundles())
                    .setExportPackages(previousParsedBundle.getExportPackages())
                    .setImportPackages(previousParsedBundle.getImportPackages())
                    .setRequiredFragments(previousParsedBundle.getRequireFragments())
                    .setFragmentHost(previousParsedBundle.getFragmentHost())
                    .setStartLevel(startLevel)
                    .setRequiredExecutionEnvironment(previousParsedBundle.getRequiredJava())
                    .createBundleInfo();
                result.addBundle(newParsedBundle);
            }
            return;
        }

        var pluginsFoldersPaths = PathsManager.INSTANCE.getBundlesLocations();

        List<BundleInfo> bundleInfos = new ArrayList<>();
        for (Path pluginsFoldersPath : pluginsFoldersPaths) {
            var correctedFolderName = correctFolderName(bundleInfo.getFirst());
            File pluginJarOrFolder = FileUtils.findFirstChildByPackageName(pluginsFoldersPath, correctedFolderName);
            if (pluginJarOrFolder != null) {
                BundleInfo info = extractBundleInfo(pluginJarOrFolder, startLevel);
                if (info != null) {
                    if (VersionRange.isVersionsCompatible(bundleInfo.getSecond(), new Version(info.getBundleVersion()))) {
                        bundleInfos.add(info);
                    }
                }
            }
        }
        if (bundleInfos.size() == 1) {
            Optional<RemoteP2BundleInfo> maxVersionRemoteBundle = BundleUtils.getMaxVersionRemoteBundle(bundleInfo, cache);
            if (maxVersionRemoteBundle.isPresent() && BundleUtils.isRemoteBundleVersionGreater(maxVersionRemoteBundle.get(), bundleInfos.get(0))) {
                maxVersionRemoteBundle.get().resolveBundle();
                    parseBundleInfo(result, maxVersionRemoteBundle.get(), cache, graph);
            } else {
                    parseBundleInfo(result, bundleInfos.get(0), cache, graph);
            }
        } else if (bundleInfos.isEmpty()) {
            Optional<RemoteP2BundleInfo> remoteP2BundleInfos = BundleUtils.getMaxVersionRemoteBundle(bundleInfo, cache);
            if (remoteP2BundleInfos.isEmpty()) {
                log.error("Couldn't find plugin '{}'", bundleInfo);
            } else {
                remoteP2BundleInfos.stream().findFirst().get().resolveBundle();
                parseBundleInfo(result, remoteP2BundleInfos.stream().findFirst().get(), cache, graph);
            }

        } else {
            var bundlesPaths = bundleInfos.stream()
                .map(it -> it.getPath().toString())
                .collect(Collectors.joining("\n  "));
            log.debug("Found multiple plugins '{}'. First will be used.\n  {}", bundleInfo, bundlesPaths);

            parseBundleInfo(result, bundleInfos.get(0), cache, graph);
        }
        if (currentFeature != null && !bundleInfos.isEmpty()) {
            currentFeature.addBundleDependency(previousParsedBundle);
        }
    }

    private static @Nonnull String correctFolderName(@Nonnull String nameToCorrect) {
        var correctedName = correctedFolderNames.get(nameToCorrect);
        return correctedName == null
            ? nameToCorrect
            : correctedName;
    }

    private static @Nullable BundleInfo extractBundleInfo(
        @Nonnull File pluginJarOrFolder,
        @Nullable Integer startLevel
    ) {
        try {
            if (pluginJarOrFolder.isDirectory()) {
                try (var inputStream = Files.newInputStream(pluginJarOrFolder.toPath().resolve(MANIFEST_PATH))) {
                    return ManifestParser.parseManifest(pluginJarOrFolder.toPath(), startLevel, new Manifest(inputStream));
                }
            }
            try (var jarFile = new JarFile(pluginJarOrFolder)) {
                return ManifestParser.parseManifest(pluginJarOrFolder.toPath(), startLevel, jarFile.getManifest());
            }
        } catch (IOException e) {
            log.warn("couldn't extract bundle info for " + pluginJarOrFolder, e);
            return null;
        }
    }

    public static void resolveTestBundlesAndLibraries(Result result, DependencyGraph graph) throws IOException {
        PathsManager manager = PathsManager.INSTANCE;
        P2BundleLookupCache lookupCache = P2RepositoryManager.INSTANCE.getLookupCache();


        Set<String> testLibraries = manager.getTestLibraries();
        Set<BundleInfo> testLibrariesBundles = new LinkedHashSet<>();
        if (testLibraries != null) {
            for (String testLibrary : testLibraries) {
                Set<BundleInfo> bundleByName = result.getBundlesByName(testLibrary);
                if (bundleByName == null) {
                    var pluginsFoldersPaths = PathsManager.INSTANCE.getBundlesLocations();

                    List<BundleInfo> bundleInfos = pluginsFoldersPaths.stream()
                        .map(pluginFolderPath -> {
                            var correctedFolderName = correctFolderName(testLibrary);
                            return FileUtils.findFirstChildByPackageName(pluginFolderPath, correctedFolderName);
                        })
                        .filter(Objects::nonNull)
                        .map(pluginJarOrFolder -> extractBundleInfo(pluginJarOrFolder, 0))
                        .filter(Objects::nonNull)
                        .toList();
                    if (!bundleInfos.isEmpty()) {
                        bundleByName = new HashSet<>(bundleInfos);
                        for (BundleInfo bundleInfo : bundleByName) {
                            result.addBundle(bundleInfo);
                            testLibrariesBundles.add(bundleInfo);
                        }
                    } else {
                        bundleByName = new HashSet<>(bundleInfos);
                        Collection<RemoteP2BundleInfo> remoteBundlesByName = lookupCache.getRemoteBundlesByName(
                            testLibrary);
                        Optional<RemoteP2BundleInfo> remoteP2BundleInfo = remoteBundlesByName.stream().findFirst();
                        if (remoteP2BundleInfo.isPresent()) {
                            remoteP2BundleInfo.get().resolveBundle();
                            bundleByName.add(remoteP2BundleInfo.get());
                            for (BundleInfo bundleInfo : bundleByName) {
                                result.addBundle(bundleInfo);
                                testLibrariesBundles.add(bundleInfo);
                            }
                        }
                    }
                }
                if (bundleByName != null) {
                    for (BundleInfo bundleInfo : bundleByName) {
                        result.addBundle(bundleInfo);
                    }
                }
            }
        }
        Collection<Path> testBundlesPaths = manager.getTestBundlesPaths();
        for (Path testBundlesPath : testBundlesPaths) {
            if (testBundlesPath.toFile().exists() && testBundlesPath.toFile().isDirectory()) {
                List<BundleInfo> bundlesToResolve = new ArrayList<>();
                for (File file : testBundlesPath.toFile().listFiles()) {
                    if (file.isDirectory()) {
                        var manifestFile = file.toPath().resolve(MANIFEST_PATH).toFile();
                        if (!manifestFile.exists()) {
                            continue;
                        }
                        try (var inputStream = new FileInputStream(manifestFile)) {
                            var manifest = new Manifest(inputStream);
                            BundleInfo bundleInfo = ManifestParser.parseManifest(file.toPath(), null, manifest);
                            if (bundleInfo != null) {
                                result.addBundle(bundleInfo);
                                bundlesToResolve.add(bundleInfo);
                            }
                        }
                    }
                }
                bundlesToResolve.addAll(testLibrariesBundles);
                for (BundleInfo bundleInfo : bundlesToResolve) {
                    for (Pair<String, VersionRange> requireBundle : bundleInfo.getRequireBundles()) {
                        resolvePluginDependencies(result, requireBundle, null, lookupCache, graph);
                    }
                    if (bundleInfo.getFragmentHost() != null) {
                        resolvePluginDependencies(result, bundleInfo.getFragmentHost(), null, lookupCache, graph);
                    }
                }
            }
            }

        }

    private static void parseBundleInfo(
        @Nonnull Result result,
        @Nonnull BundleInfo bundleInfo,
        P2BundleLookupCache cache,
        DependencyGraph graph
    ) throws IOException {
        result.addBundle(bundleInfo);
        DependencyGraph.DependencyNode previousNode = graph.addCurrentNodeDependencyAndTraverse(bundleInfo.getBundleName());
        try {
            if (bundleInfo.getFragmentHost() != null) {
                BundleInfo hostBundle = getHostBundle(result, bundleInfo, cache);
                if (hostBundle != null) {
                    hostBundle.addFragmentBundle(bundleInfo);
                    graph.addDependency(hostBundle.getBundleName(), bundleInfo.getBundleName());
                } else {
                    log.error("Fragment host bundle not found");
                }
            }
            for (var requireBundle : bundleInfo.getRequireBundles()) {
                PluginResolver.resolvePluginDependencies(result, requireBundle, null, cache, graph);
            }
        } finally {
            graph.setCurrentNode(previousNode);
        }
    }

    @org.jkiss.code.Nullable
    private static BundleInfo getHostBundle(@NotNull Result result,
                                            @NotNull BundleInfo bundleInfo,
                                            P2BundleLookupCache cache) {
        if (bundleInfo.getFragmentHost() == null) {
            return null;
        }
        Set<BundleInfo> hostBundles = result.getBundlesByName(bundleInfo.getFragmentHost().getFirst());
        BundleInfo hostBundle = null;
        if (CommonUtils.isEmpty(hostBundles) || hostBundles.stream().noneMatch(it -> VersionRange.isVersionsCompatible(bundleInfo.getFragmentHost().getSecond(), new Version(it.getBundleVersion())))) {
            hostBundle = cache.getRemoteBundlesByName(bundleInfo.getFragmentHost().getFirst()).stream().filter(it -> VersionRange
                .isVersionsCompatible(bundleInfo.getFragmentHost().getSecond(), new Version(it.getBundleVersion()))).findFirst().orElse(null);
        } else if (hostBundles.stream().anyMatch(it -> VersionRange.isVersionsCompatible(bundleInfo.getFragmentHost().getSecond(), new Version(it.getBundleVersion())))) {
            hostBundle = hostBundles.stream().filter(it -> VersionRange.isVersionsCompatible(bundleInfo.getFragmentHost().getSecond(), new Version(it.getBundleVersion()))).findFirst().get();
        }
        return hostBundle;
    }
}