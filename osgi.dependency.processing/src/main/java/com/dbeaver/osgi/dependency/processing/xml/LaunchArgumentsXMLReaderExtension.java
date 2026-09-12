package com.dbeaver.osgi.dependency.processing.xml;

import com.dbeaver.osgi.dependency.processing.PathsManager;
import com.dbeaver.osgi.dependency.processing.Result;
import com.dbeaver.osgi.dependency.processing.util.DependencyGraph;

import javax.xml.stream.events.Comment;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LaunchArgumentsXMLReaderExtension extends XmlReaderExtension {
    // regex to match CLI args
    public static final Pattern CLI_REGEX = Pattern.compile("(?<=\\s|^)-{1,2}\\S*(?:\\s+[^\\s-]\\S*)?");
    private static final Pattern PARAMETERS_MARKER = Pattern.compile(
        "dbeaver-launch-parameters\\s*:\\s*([a-zA-Z0-9_.-]+(?:\\s*,\\s*[a-zA-Z0-9_.-]+)*)"
    );

    @Override
    public void resolveStartElement(
        Result result,
        StartElement startElement,
        XMLEventReader reader,
        DependencyGraph graph
    ) throws XMLStreamException {
        if ("vmArgs".equals(startElement.getName().getLocalPart())) {
            String[] strings = extractArgs(reader, startElement.getName().getLocalPart());
            result.getArguments().setVmARGS(strings);
        }
        if ("vmArgsMac".equals(startElement.getName().getLocalPart())) {
            String[] strings = extractArgs(reader, startElement.getName().getLocalPart());
            result.getArguments().setVmARGSMac(strings);
        }
        if ("programArgs".equals(startElement.getName().getLocalPart())) {
            String[] strings = extractArgs(reader, startElement.getName().getLocalPart());
            result.getArguments().setProgramARGS(strings);
        }
        if ("programArgsMac".equals(startElement.getName().getLocalPart())) {
            String[] strings = extractArgs(reader, startElement.getName().getLocalPart());
            result.getArguments().setGetProgramARGSMacOS(strings);
        }
    }

    private String[] extractArgs(XMLEventReader reader, String startElement) throws XMLStreamException {
        Path parametersDirectory = PathsManager.INSTANCE.getProjectsFolderPath()
            .resolve("dbeaver")
            .resolve("product")
            .resolve("launch-parameters");
        return extractArgs(reader, startElement, parametersDirectory);
    }

    static String[] extractArgs(XMLEventReader reader, String startElement, Path parametersDirectory)
        throws XMLStreamException {
        StringBuilder args = new StringBuilder();

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isEndElement() && startElement.equals(event.asEndElement().getName().getLocalPart())) {
                break;
            }
            if (event.isCharacters()) {
                args.append(event.asCharacters().getData().trim()).append(" ");
            } else if (event instanceof Comment comment) {
                appendLaunchParameters(args, comment.getText().trim(), parametersDirectory);
            }
        }
        Matcher matcher = CLI_REGEX.matcher(args);

        List<String> argsList = new ArrayList<>();

        while (matcher.find()) {
            argsList.add(matcher.group());
        }
        return argsList.toArray(new String[0]);
    }

    private static void appendLaunchParameters(StringBuilder args, String comment, Path parametersDirectory)
        throws XMLStreamException {
        Matcher marker = PARAMETERS_MARKER.matcher(comment);
        if (!marker.matches()) {
            return;
        }
        for (String parameterSet : marker.group(1).split(",")) {
            String name = parameterSet.trim();
            Path parameterFile = parametersDirectory.resolve(name + ".ini");
            try {
                if (!Files.isRegularFile(parameterFile)) {
                    throw new XMLStreamException("Unknown launch parameter set '" + name + "': " + parameterFile);
                }
                Files.readAllLines(parameterFile, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(line -> args.append(line).append(' '));
            } catch (IOException e) {
                throw new XMLStreamException("Error reading launch parameter set '" + name + "'", e);
            }
        }
    }
}

