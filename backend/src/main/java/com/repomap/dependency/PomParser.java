package com.repomap.dependency;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/** Parse a Maven POM file and return the declared dependencies. */
public class PomParser {

    private final File pomFile;

    public PomParser(File pomFile) {
        this.pomFile = pomFile;
    }

    /** Return the declared (non-transitive) dependencies as a Map of (key -> DependencySymbol). */
    public Map<String, DependencySymbol> getDependencies() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entities for security
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomFile);
            doc.getDocumentElement().normalize();

            Map<String, DependencySymbol> result = new LinkedHashMap<>();

            // <dependencies> -> <dependency> blocks
            NodeList deps = doc.getElementsByTagName("dependency");
            for (int i = 0; i < deps.getLength(); i++) {
                org.w3c.dom.Element dep = (org.w3c.dom.Element) deps.item(i);
                String groupId = readText(dep, "groupId");
                String artifactId = readText(dep, "artifactId");
                String version = readText(dep, "version");

                if (groupId != null && artifactId != null) {
                    String key = groupId + ":" + artifactId;
                    result.put(key, new DependencySymbol(groupId, artifactId, version));
                }
            }
            return result;
        } catch (Exception ex) {
            throw new RuntimeException("Could not parse POM: " + pomFile.getAbsolutePath(), ex);
        }
    }

    private static String readText(org.w3c.dom.Element parent, String childTag) {
        NodeList children = parent.getElementsByTagName(childTag);
        if (children.getLength() > 0) {
            return children.item(0).getTextContent().trim();
        }
        return null;
    }
}