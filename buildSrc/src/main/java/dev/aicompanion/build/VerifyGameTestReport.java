package dev.aicompanion.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@DisableCachingByDefault(because = "Every game server invocation must produce a complete fresh report")
public abstract class VerifyGameTestReport extends DefaultTask {
    // Directories may be absent after a startup failure; report that as a test failure.
    @Internal public abstract DirectoryProperty getReportDirectory();
    @Internal public abstract DirectoryProperty getGameDirectory();

    @TaskAction
    public void verify() throws IOException {
        var reports = getReportDirectory().get().getAsFile().toPath();
        Files.createDirectories(reports);
        var serverLog = getGameDirectory().get().getAsFile().toPath().resolve("logs/latest.log");
        if (Files.isRegularFile(serverLog)) {
            Files.copy(serverLog, reports.resolve("server.log"), StandardCopyOption.REPLACE_EXISTING);
        }
        var result = GameTestResults.read(reports);
        var exitCodeFile = reports.resolve("exit-code.txt");
        int exitCode = Files.isRegularFile(exitCodeFile) ? Integer.parseInt(Files.readString(exitCodeFile).trim()) : -1;
        String summary = result.summary(exitCode);
        Files.writeString(reports.resolve("summary.txt"), summary);
        getLogger().lifecycle(summary);
        if (!result.successful(exitCode)) {
            throw new GradleException("GameTests failed. See " + reports.resolve("summary.txt"));
        }
    }
}
