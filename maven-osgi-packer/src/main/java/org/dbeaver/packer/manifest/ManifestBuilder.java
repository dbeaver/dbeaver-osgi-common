package org.dbeaver.packer.manifest;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class ManifestBuilder {
    private static final String MANIFEST_VERSION = "Manifest-Version";
    private static final String BUNDLE_MANIFEST_VERSION = "Bundle-ManifestVersion";
    private static final String BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName";
    private static final String BUNDLE_VERSION = "Bundle-Version";
    private static final String BUNDLE_NAME = "Bundle-Name";
    private static final String BUNDLE_ACTIVATION_POLICY = "Bundle-ActivationPolicy";
    private static final String BUNDLE_REQUIRED_EXECUTION_ENVIRONMENT = "Bundle-RequiredExecutionEnvironment";
    private static final String BUNDLE_CLASS_PATH = "Bundle-ClassPath";
    private static final String EXPORT_PACKAGE = "Export-Package";

    public static String buildManifest(
        String symbolicName,
        String moduleName,
        String moduleVersion,
        List<String> classpaths,
        Path basedir,
        Path fragPath
    ) {

        StringBuilder manifest = new StringBuilder();
        if (classpaths.size() == 1) {
            return buildFromExistingManifest(classpaths, basedir);
        }
        return generateManifest(symbolicName, moduleName, moduleVersion, classpaths, basedir, fragPath, manifest);
    }

    private static String buildFromExistingManifest(List<String> classpaths, Path basedir) {
        // if there is only one classpath, we need to check if it's not already an OSGI project
        String classpath = classpaths.get(0);
        Path osgiBundlePath = basedir.resolve("../../target-bundles").resolve(basedir.getFileName()).normalize();
        StringBuilder manifest = new StringBuilder();
        if (classpath.endsWith(".jar")) {
            try (JarFile jarFile = new JarFile(classpath)) {
                Manifest mf = jarFile.getManifest();
                if (mf != null && mf.getMainAttributes().getValue(BUNDLE_SYMBOLIC_NAME) != null) {
                    // this is already an OSGI bundle, we can use its manifest directly, but replace classpath to jar
                    // Use all except classpath for bundle generation
                    mf.getMainAttributes().forEach((key, value) -> {
                        if (!key.toString().equals(BUNDLE_CLASS_PATH)) {
                            manifest.append(key).append(": ").append(trimBySize((String) value)).append("\n");
                        }
                    });
                    manifest.append(BUNDLE_CLASS_PATH + ": \n");
                    manifest.append(" ").append(osgiBundlePath.relativize(Paths.get(classpath)).toString());
                    if (!classpaths.get(classpaths.size() - 1).equals(classpath)) {
                        manifest.append(",\n");
                    }
                    manifest.append("\n");
                }
            } catch (IOException e) {
                System.out.println("Error reading jar file: " + e.getMessage());
            }
        }
        return manifest.toString();
    }

    private static void addIfExists(StringBuilder manifestBuilder, Manifest mf, String key) {
        if (mf.getAttributes(key) != null) {
            manifestBuilder.append(key).append(": ").append(mf.getAttributes(key)).append("\n");
        }
    }

    private static String generateManifest(
        String symbolicName,
        String moduleName,
        String moduleVersion,
        List<String> classpaths,
        Path basedir,
        Path fragPath,
        StringBuilder manifest
    ) {
        manifest.append(MANIFEST_VERSION + ": 1.0\n");
        manifest.append(BUNDLE_MANIFEST_VERSION + ": 2\n");
        manifest.append(BUNDLE_SYMBOLIC_NAME + ": ").append(symbolicName).append("\n");
        manifest.append(BUNDLE_VERSION + ": ").append(adaptVersion(moduleVersion)).append("\n");
        manifest.append(BUNDLE_NAME + ": ").append(moduleName).append("\n");
        manifest.append(BUNDLE_ACTIVATION_POLICY + ": lazy\n");
        manifest.append(BUNDLE_REQUIRED_EXECUTION_ENVIRONMENT + ": JavaSE-17\n");
        // write classpath
        manifest.append(BUNDLE_CLASS_PATH + ": \n");
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
                            if (key.toString().startsWith(EXPORT_PACKAGE)) {
                                String pkg = !v.contains(";") ? v : v.split(";")[0].trim();
                                if (packages.contains(pkg)) {
                                    return;
                                }
                                packages.add(pkg);
                                StringJoiner joiner = trimBySize(v);

                                if (!hasExportPackage[0]) {
                                    currentClasspathContainsExportPackage.set(true);
                                    manifest.append(key).append(": \n ").append(joiner);
                                    hasExportPackage[0] = true;
                                } else {
                                    manifest.append(",\n ").append(joiner);
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
                                        manifest.append(EXPORT_PACKAGE).append(": \n ").append(pkg);
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

    private static StringJoiner trimBySize(String v) {
        // if v is bigger than 100 characters, split it into multiple lines in for loop
        StringJoiner joiner = new StringJoiner("\n ");
        for (int i = 0; i < v.length(); i += 100) {
            joiner.add(v.substring(i, Math.min(i + 100, v.length())));
        }
        return joiner;
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
                if (key.toString().startsWith(EXPORT_PACKAGE)) {
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
