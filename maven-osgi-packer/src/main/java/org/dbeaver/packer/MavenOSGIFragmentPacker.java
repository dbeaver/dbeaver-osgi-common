/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
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
package org.dbeaver.packer;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.dbeaver.packer.manifest.ManifestBuilder;
import org.dbeaver.packer.pom.PomBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
@Mojo(name = "create-osgi-bundle")
public class MavenOSGIFragmentPacker extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;
    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File basedir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {

            // parse pom.xml
            Path basedir = project.getBasedir().toPath();
            String targetBundleId = project.getGroupId() + "." + project.getArtifactId();

            // Assuming project base dir is current dir
            Path metaFolder = basedir.resolve("META-INF");
            if (!Files.exists(metaFolder)) {
                return;
            }
            Path targetBundlesPath = basedir.resolve("../../target-bundles");
            patchTargetPom(targetBundlesPath, project);
            Path bundlePath = targetBundlesPath.resolve(targetBundleId);
            // Delete recursively
            if (Files.exists(bundlePath)) {
                try (Stream<Path> walk = Files.walk(bundlePath)) {
                    walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                }
            }
            Files.createDirectories(bundlePath);
            Path fragPath = metaFolder.resolve("FRAG.FMF");
            Path lib = bundlePath.resolve("lib");
            Files.createDirectories(lib);

            Path resolve = Files.createDirectories(bundlePath.resolve("META-INF"));
            Path manifestPath = resolve.resolve("MANIFEST.MF");
            Files.deleteIfExists(manifestPath);
            if (Files.exists(fragPath) && Files.isRegularFile(fragPath)) {
                System.out.println("Found FRAG.FMF at: " + fragPath.toAbsolutePath());
            } else {
                System.out.println("FRAG.FMF not found in META-INF directory.");
            }

            // Copy META-INF to bundlePath
            Path pomFile = basedir.resolve("pom.xml");
            ParseResult parseResult = parsePomFile(pomFile);
            String moduleVersion = parseResult.version;
            List<Path> classpathList = new ArrayList<>();
            transferAndIndexLibraries(basedir, lib, classpathList);
            writeManifest(
                targetBundleId,
                targetBundleId,
                moduleVersion,
                classpathList,
                basedir,
                fragPath,
                manifestPath
            );
            writeBuildProperties(bundlePath);
            writePOM(bundlePath, parseResult, basedir);

        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new MojoExecutionException(e);
        }

    }

    private void patchTargetPom(Path targetBundlesPath, MavenProject project) throws IOException {
        if (!Files.exists(targetBundlesPath)) {
            Files.createDirectories(targetBundlesPath);
        }
        Path pom = targetBundlesPath.resolve("pom.xml");
        if (!Files.exists(pom)) {
            Files.writeString(pom,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project\n" +
                "        xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\"\n" +
                "        xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "\n" +
                "    <artifactId>target-bundles</artifactId>\n" +
                "    <packaging>pom</packaging>\n" +
                "    <version>1.0.0-SNAPSHOT</version>\n" +
                "    <parent>\n" +
                "        <groupId>com.dbeaver.osgi</groupId>\n" +
                "        <artifactId>dbeaver-deps-p2</artifactId>\n" +
                "        <version>1.0.0-SNAPSHOT</version>\n" +
                "        <relativePath>../pom.xml</relativePath>\n" +
                "    </parent>\n" +
                "    <modules>\n" +
                "    </modules>\n" +
                "</project>",
                StandardCharsets.UTF_8
            );
        }
        String pomText = Files.readString(pom);
        String moduleRef = "        <module>" + project.getGroupId() + "." + project.getArtifactId() + "</module>\n";
        if (pomText.contains(moduleRef)) {
            return;
        }
        int insertPos = pomText.indexOf("    </modules>");
        pomText = pomText.substring(0, insertPos) +
            moduleRef +
            pomText.substring(insertPos);
        Files.writeString(pom, pomText);
    }

    private static void transferAndIndexLibraries(Path basedir, Path lib, List<Path> classpathList) throws IOException {
        Path baseLib = basedir.resolve("lib");
        if (Files.exists(baseLib)) {
            try (Stream<Path> list = Files.list(baseLib)) {
                list.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".jar")).forEach(p -> {
                    try {
                        Files.move(p, lib.resolve(p.getFileName()));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    classpathList.add(lib.resolve(p.getFileName()));
                });
            }
        }
        Files.delete(baseLib);
    }

    private static void writeBuildProperties(Path bundlePath) throws IOException {
        Path buildProperties = bundlePath.resolve("build.properties");
        Files.createFile(buildProperties);
        Files.write(buildProperties, ManifestBuilder.getDefaultBuildProperties().getBytes());
    }

    private static void writeManifest(
        String symbolicName,
        String moduleName,
        String moduleVersion,
        List<Path> classpathList,
        Path basedir,
        Path fragPath,
        Path manifestPath
    ) throws IOException {
        String builtManifest =
            ManifestBuilder.buildManifest(
                symbolicName,
                moduleName,
                moduleVersion,
                classpathList,
                basedir,
                fragPath
            );
        Files.createFile(manifestPath);
        Files.write(manifestPath, builtManifest.getBytes());
    }

    private static void writePOM(Path bundlePath, ParseResult parseResult, Path basedir) throws IOException {
        Path pom = bundlePath.resolve("pom.xml");
        Files.deleteIfExists(pom);
        Files.createFile(pom);
        String buildPom  = PomBuilder.buildPom(
            parseResult.groupId,
            parseResult.groupId + "." + parseResult.artifactId,
            parseResult.version
        );
        Files.write(pom, buildPom.getBytes());
    }
    // pars

    private static ParseResult parsePomFile(Path pom) throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc;
        try (InputStream is = Files.newInputStream(pom)) {
            doc = builder.parse(is);
        }
        doc.getDocumentElement().normalize();

        // Extract main artifact details.
        String groupId = getTagValue(doc, "groupId");
        // Fallback: if groupId is not defined on the project, check the parent.
        if (groupId == null || groupId.isEmpty()) {
            NodeList parentNodes = doc.getElementsByTagName("parent");
            if (parentNodes != null && parentNodes.getLength() > 0) {
                Element parentElement = (Element) parentNodes.item(0);
                groupId = getTagValue(parentElement, "groupId");
            }
        }
        String artifactId = getTagValue(doc, "artifactId");
        String version = getTagValue(doc, "version");
        return new ParseResult(groupId, artifactId, version, null);
    }
    // Helper method to retrieve the text content of a given tag from an Element.

    private static String getTagValue(Element element, String tag) {
        NodeList nodeList = element.getElementsByTagName(tag);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent().trim();
        }
        return null;
    }
    // Helper method to retrieve the text content of the first occurrence of a given tag from the Document.

    private static String getTagValue(Document doc, String tag) {
        NodeList nodeList = doc.getElementsByTagName(tag);
        if (nodeList != null && nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent().trim();
        }
        return null;
    }

    private record ParseResult(String groupId, String artifactId, String version, String classifier) {
    }
}
