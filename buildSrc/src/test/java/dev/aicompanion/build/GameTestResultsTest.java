package dev.aicompanion.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GameTestResultsTest {
    @TempDir Path directory;

    private GameTestResults report(String selection, String cases) throws Exception {
        Files.writeString(directory.resolve("selected-tests.txt"), selection);
        Files.writeString(directory.resolve("results.xml"), "<testsuite>" + cases + "</testsuite>");
        return GameTestResults.read(directory);
    }

    @Test void completeRunPassesRegardlessOfReportOrder() throws Exception {
        var result = report("Example.first\nExample.second\n",
                "<testcase name='Example.second'/><testcase name='Example.first'/>");
        assertTrue(result.successful(0));
        assertEquals(2, result.passed());
    }

    @Test void assertionOrErrorFailsEvenWithZeroExit() throws Exception {
        for (String kind : new String[]{"failure", "error"}) {
            var result = report("Example.first\n",
                    "<testcase name='Example.first'><" + kind + " message='lost items'/></testcase>");
            assertFalse(result.successful(0));
            assertEquals("Example.first: lost items", result.failures().getFirst());
        }
    }

    @Test void optionalFailureIsReportedSeparately() throws Exception {
        var result = report("Example.first\n", "<testcase name='Example.first'><skipped message='optional'/></testcase>");
        assertTrue(result.successful(0));
        assertEquals(0, result.passed());
        assertEquals(1, result.optionalFailures().size());
    }

    @Test void partialDuplicateAndUnexpectedResultsFail() throws Exception {
        for (String cases : new String[]{"<testcase name='Example.first'/>",
                "<testcase name='Example.first'/><testcase name='Example.first'/>",
                "<testcase name='Example.first'/><testcase name='Example.other'/>"}) {
            assertFalse(report("Example.first\nExample.second\n", cases).successful(0));
        }
    }

    @Test void emptyBlankAndDuplicateSelectionsFail() throws Exception {
        assertFalse(report("", "").successful(0));
        assertFalse(report("\n", "<testcase name=''/>").successful(0));
        assertFalse(report("Example.first\nExample.first\n",
                "<testcase name='Example.first'/><testcase name='Example.first'/>").successful(0));
    }

    @Test void missingReportPreservesSelectedCount() throws Exception {
        assertFalse(GameTestResults.read(directory).successful(0));
        Files.writeString(directory.resolve("selected-tests.txt"), "Example.first\n");
        var result = GameTestResults.read(directory);
        assertFalse(result.successful(0));
        assertEquals(1, result.selected());
    }

    @Test void malformedXmlAndExternalEntitiesFail() throws Exception {
        Files.writeString(directory.resolve("selected-tests.txt"), "Example.first\n");
        for (String xml : new String[]{"<broken", "<!DOCTYPE testsuite SYSTEM 'file:///missing'><testsuite/>"}) {
            Files.writeString(directory.resolve("results.xml"), xml);
            assertFalse(GameTestResults.read(directory).successful(0));
        }
    }

    @Test void abnormalExitCannotPassWithCompleteReport() throws Exception {
        var result = report("Example.first\n", "<testcase name='Example.first'/>");
        assertFalse(result.successful(1));
        assertFalse(result.successful(-1));
        assertTrue(result.summary(-1).startsWith("FAILED\n"));
    }
}
