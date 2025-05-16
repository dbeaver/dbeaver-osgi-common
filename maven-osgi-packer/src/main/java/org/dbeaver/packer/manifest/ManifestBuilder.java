package org.dbeaver.packer.manifest;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class ManifestBuilder {
    public static String buildManifest(
        String symbolicName,
        String moduleName,
        String moduleVersion,
        List<String> classpaths,
        Path basedir,
        Path fragPath
    ) {

        StringBuilder manifest = new StringBuilder();
        manifest.append("Manifest-Version: 1.0\n");
        manifest.append("Bundle-ManifestVersion: 2\n");
        manifest.append("Bundle-SymbolicName: ").append(symbolicName).append("\n");
        manifest.append("Bundle-Version: ").append(adaptVersion(moduleVersion)).append("\n");
        manifest.append("Bundle-Name: ").append(moduleName).append("\n");
        manifest.append("Bundle-ActivationPolicy: lazy\n");
        manifest.append("Bundle-RequiredExecutionEnvironment: JavaSE-17\n");
        // write classpath
        manifest.append("Bundle-ClassPath: \n");
        Path osgiBundlePath = basedir.resolve("../../target-bundles").resolve(basedir.getFileName()).normalize();
        for (String classpath : classpaths) {
            manifest.append(" ").append(osgiBundlePath.relativize(Paths.get(classpath)).toString());
            if (!classpaths.get(classpaths.size() - 1).equals(classpath)) {
                manifest.append(",\n");
            }
        }

        manifest.append("\n");
        // Real all jar files in classpath, extract packages as exports, if packages have no exports in manifest get all packages
        final boolean[] hasExportPackage = {false};
        provideDependencies(fragPath, manifest, hasExportPackage);
        if (!hasExportPackage[0]) {
            Set<String> packages = new LinkedHashSet<>();
            for (String classpath : classpaths) {
                try (JarFile jarFile = new JarFile(classpath)) {
                    Manifest mf = jarFile.getManifest();
                    AtomicBoolean currentClasspathContainsExportPackage = new AtomicBoolean(false);
                    if (mf != null) {
                        mf.getMainAttributes().forEach((key, value) -> splitByCommaOutsideQuotes(value.toString()).forEach(v -> {
                            if (key.toString().startsWith("Export-Package")) {
                                String pkg = !v.contains(";") ? v : v.split(";")[0].trim();
                                if (packages.contains(pkg)) {
                                    return;
                                }
                                packages.add(pkg);
                                if (!hasExportPackage[0]) {
                                    currentClasspathContainsExportPackage.set(true);
                                    manifest.append(key).append(": \n ").append(v);
                                    hasExportPackage[0] = true;
                                } else {
                                    manifest.append(",\n ").append(v);
                                    currentClasspathContainsExportPackage.set(true);
                                }
                            }
                        }));
                    }
                    if (!currentClasspathContainsExportPackage.get()) {
                        // get all packages from the jar file outside manifest
                        jarFile.stream().filter(e -> e.getName().endsWith(".class")).filter(e -> !e.getName().contains("META-INF"))
                            .forEach(e -> {
                                String className = e.getName();
                                int lastSlash = className.lastIndexOf('/');
                                if (lastSlash > 0) {
                                    String pkg = className.substring(0, lastSlash).replace('/', '.');
                                    if (packages.contains(pkg)) {
                                        return;
                                    }
                                    packages.add(pkg);
                                    if (!hasExportPackage[0]) {
                                        manifest.append("Export-Package").append(": \n ").append(pkg);
                                        hasExportPackage[0] = true;
                                    } else {
                                        manifest.append(",\n ").append(pkg);
                                    }
                                }
                            });
                    }
                } catch (IOException e) {
                    System.out.println("Error reading jar file: " + e.getMessage());
                }
            }
        }
        manifest.append("\n");
        // dependencies, extract parameters from fragPath .MF file\
        manifest.append("\n");
        return manifest.toString();
    }

    public static String getDefaultBuildProperties() {
        return """
            source.. =
            bin.includes = META-INF/,\\
                           lib/
            src.includes = META-INF/
            """;
    }

    private static void provideDependencies(Path fragPath, StringBuilder manifest, boolean[] hasExportPackage) {
        try (FileInputStream fos = new FileInputStream(fragPath.toFile())) {
            Manifest mf = new Manifest(fos);
            mf.getMainAttributes().forEach((key, value) -> {
                if (key.toString().startsWith("Export-Package")) {
                    hasExportPackage[0] = true;
                }
                boolean isFirst = true;
                List<String> strings = splitByCommaOutsideQuotes(value.toString());
                for (String string : strings) {
                    if (isFirst) {
                        manifest.append(key).append(": \n ").append(string);
                        isFirst = false;
                    } else {
                        manifest.append(",\n ").append(string);
                    }
                }
                manifest.append("\n");
            });
        } catch (Exception e) {
            System.out.println("Error extracting dependencies: " + e.getMessage());
        }
    }

    private static String adaptVersion(String moduleVersion) {
        //adapt version to OSGI format
        String[] parts = moduleVersion.split("\\.");
        StringBuilder adaptedVersion = new StringBuilder();
        boolean isFirst = true;
        for (String part : parts) {
            if (part.matches("\\d+")) {
                if (isFirst) {
                    adaptedVersion.append(part);
                    isFirst = false;
                } else {
                    adaptedVersion.append(".").append(part);
                }
            } else {
                if (isFirst) {
                    adaptedVersion.append(part.replaceAll("[^a-zA-Z0-9]", "_"));
                    isFirst = false;
                } else {
                    adaptedVersion.append(".").append(part.replaceAll("[^a-zA-Z0-9]", "_"));
                }

            }
        }
        // SNAPSHOT versions are not allowed in OSGI remove them
        if (adaptedVersion.toString().endsWith("_SNAPSHOT")) {
            adaptedVersion.delete(adaptedVersion.length() - 9, adaptedVersion.length());
        }
        return adaptedVersion.toString();
    }

    private static List<String> splitByCommaOutsideQuotes(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '"') {
                insideQuotes = !insideQuotes; // Toggle quote mode
                current.append(c);
            } else if (c == ',' && !insideQuotes) {
                result.add(current.toString());
                current.setLength(0); // Reset buffer
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            result.add(current.toString());
        }

        return result;
    }

}
