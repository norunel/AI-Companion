package dev.aicompanion.build;

import org.w3c.dom.Element;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Checks the completed report against the inventory captured before execution. */
public record GameTestResults(int selected, int completed, int passed,
                              List<String> failures, List<String> optionalFailures, List<String> errors) {
    public GameTestResults {
        failures = List.copyOf(failures);
        optionalFailures = List.copyOf(optionalFailures);
        errors = List.copyOf(errors);
    }

    public boolean successful(int exitCode) {
        return exitCode == 0 && selected > 0 && errors.isEmpty() && failures.isEmpty();
    }

    public String summary(int exitCode) {
        var text = new StringBuilder(successful(exitCode) ? "PASSED\n" : "FAILED\n");
        text.append("Process exit code: ").append(exitCode).append('\n');
        text.append("Selected: ").append(selected).append(", completed: ").append(completed)
                .append(", passed: ").append(passed).append('\n');
        failures.forEach(failure -> text.append("FAIL: ").append(failure).append('\n'));
        optionalFailures.forEach(failure -> text.append("OPTIONAL: ").append(failure).append('\n'));
        errors.forEach(error -> text.append("ERROR: ").append(error).append('\n'));
        if (exitCode == -1) text.append("ERROR: Test process did not complete normally; check Gradle output for a timeout or launch failure.\n");
        return text.toString();
    }

    public static GameTestResults read(Path directory) {
        int selected = 0, completed = 0, passed = 0;
        List<String> failures = new ArrayList<>(), optional = new ArrayList<>(), errors = new ArrayList<>();
        try {
            List<String> expected = Files.readAllLines(directory.resolve("selected-tests.txt"));
            selected = expected.size();
            if (expected.isEmpty() || expected.stream().anyMatch(String::isBlank)
                    || new HashSet<>(expected).size() != selected) {
                errors.add("Test selection is empty or contains blank or duplicate names");
            }

            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException error) throws SAXParseException { throw error; }
                @Override public void fatalError(SAXParseException error) throws SAXParseException { throw error; }
            });
            var cases = builder.parse(directory.resolve("results.xml").toFile()).getElementsByTagName("testcase");
            completed = cases.getLength();
            List<String> actual = new ArrayList<>();
            for (int i = 0; i < completed; i++) {
                var test = (Element) cases.item(i);
                String name = test.getAttribute("name");
                actual.add(name);
                Element failure = child(test, "failure");
                if (failure == null) failure = child(test, "error");
                Element skipped = child(test, "skipped");
                if (failure != null) failures.add(name + ": " + failure.getAttribute("message"));
                else if (skipped != null) optional.add(name + ": " + skipped.getAttribute("message"));
                else passed++;
            }
            if (!actual.stream().sorted().toList().equals(expected.stream().sorted().toList())) {
                errors.add("Reported tests do not match the selected tests");
            }
        } catch (Exception exception) {
            errors.add("Missing or invalid test report: " + exception.getClass().getSimpleName());
        }
        return new GameTestResults(selected, completed, passed, failures, optional, errors);
    }

    private static Element child(Element parent, String name) {
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && element.getTagName().equals(name)) return element;
        }
        return null;
    }
}
