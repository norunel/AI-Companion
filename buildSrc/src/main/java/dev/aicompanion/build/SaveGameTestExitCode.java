package dev.aicompanion.build;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.tasks.JavaExec;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/** Capture the actual result at execution time, including with the configuration cache. */
public final class SaveGameTestExitCode implements Action<Task>, Serializable {
    private static final long serialVersionUID = 1L;
    private final File reportDirectory;

    public SaveGameTestExitCode(File reportDirectory) {
        this.reportDirectory = reportDirectory;
    }

    @Override
    public void execute(Task task) {
        int exitCode = ((JavaExec) task).getExecutionResult().get().getExitValue();
        try {
            Files.createDirectories(reportDirectory.toPath());
            Files.writeString(reportDirectory.toPath().resolve("exit-code.txt"), Integer.toString(exitCode));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
