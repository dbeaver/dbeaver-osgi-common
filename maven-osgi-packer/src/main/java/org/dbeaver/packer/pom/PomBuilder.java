package org.dbeaver.packer.pom;

public class PomBuilder {
    public static String buildPom(String groupId, String artifactId, String version) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
            "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 " +
            "http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "    <modelVersion>4.0.0</modelVersion>\n\n" +
            "    <groupId>" + groupId + "</groupId>\n" +
            "    <artifactId>" + artifactId + "</artifactId>\n" +
            "    <packaging>" + "eclipse-plugin" + "</packaging>\n" +
            "    <version>" + version + "</version>\n" +
            "    <parent>\n" +
            "           <groupId>com.dbeaver.osgi</groupId>" + "\n" +
            "           <artifactId>root</artifactId>" + "\n" +
            "           <version>1.0.0-SNAPSHOT</version>" + "\n" +
            "           <relativePath>../pom.xml</relativePath>" + "\n" +
            "    </parent>\n" +
            "</project>\n";
    }
}

