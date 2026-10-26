package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.gametest.framework.TestReporter;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import org.objectweb.asm.Type;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Development-only selection and reporting; excluded from the distributed mod. */
@GameTestHolder(AICompanionMod.MOD_ID)
public final class CompanionTestSuite {
    private CompanionTestSuite() { }

    @GameTestGenerator
    public static List<TestFunction> selectedTests() throws Exception {
        String reports = System.getProperty("aicompanion.testReports");
        if (reports == null) return List.of();

        String filter = System.getProperty("aicompanion.testFilter", "*");
        Pattern pattern = Pattern.compile(Pattern.quote(filter).replace("*", "\\E.*\\Q"), Pattern.CASE_INSENSITIVE);
        int maxTicks = Integer.parseInt(System.getProperty("aicompanion.testMaxTicks", "2400"));
        if (maxTicks < 0) throw new IllegalArgumentException("gameTestMaxTicks must be non-negative");

        var holders = ModList.get().getAllScanData().stream()
                .flatMap(data -> data.getAnnotations().stream())
                .filter(data -> data.annotationType().equals(Type.getType(GameTestHolder.class)))
                .map(data -> data.clazz().getClassName())
                .filter(name -> name.startsWith("dev.aicompanion."))
                .distinct().sorted().toList();
        List<TestFunction> tests = new ArrayList<>();
        for (String holder : holders) {
            Class<?> type = Class.forName(holder);
            for (Method method : type.getDeclaredMethods()) {
                GameTest test = method.getAnnotation(GameTest.class);
                String name = type.getSimpleName() + "." + method.getName();
                if (test == null || test.manualOnly() || !pattern.matcher(name).matches()
                        || (maxTicks > 0 && test.timeoutTicks() > maxTicks)) continue;
                if (!Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalArgumentException("Selected tests must be static: " + name);
                }
                if (!GameTestHooks.getTemplateNamespace(method).equals(AICompanionMod.MOD_ID)) {
                    throw new IllegalArgumentException("Unsupported template namespace: " + name);
                }
                String prefix = GameTestHooks.prefixGameTestTemplate(method)
                        ? type.getSimpleName().toLowerCase(Locale.ROOT) + "." : "";
                String template = prefix + (test.template().isEmpty()
                        ? method.getName().toLowerCase(Locale.ROOT) : test.template());
                // Keep the annotation semantics and batch hooks used by GameTestRegistry.
                tests.add(new TestFunction(test.batch(), name, "aicompanion_test:" + template,
                        StructureUtils.getRotationForRotationSteps(test.rotationSteps()),
                        test.timeoutTicks(), test.setupTicks(), test.required(), test.manualOnly(),
                        test.requiredSuccesses(), test.attempts(), test.skyAccess(), helper -> invoke(method, helper)));
            }
        }
        tests.sort(Comparator.comparing(TestFunction::testName));
        if (tests.isEmpty()) throw new IllegalArgumentException("No GameTests match " + filter + " (max ticks: " + maxTicks + ")");

        Path directory = Path.of(reports);
        Files.createDirectories(directory);
        Files.write(directory.resolve("selected-tests.txt"), tests.stream().map(TestFunction::testName).toList());
        var xml = new JUnitLikeTestReporter(directory.resolve("results.xml").toFile());
        var log = new LogTestReporter();
        GlobalTestReporter.replaceWith(new TestReporter() {
            @Override public void onTestFailed(GameTestInfo info) { log.onTestFailed(info); xml.onTestFailed(info); }
            @Override public void onTestSuccess(GameTestInfo info) { xml.onTestSuccess(info); }
            @Override public void finish() { xml.finish(); }
        });
        AICompanionMod.LOGGER.info("Selected {} GameTests: filter={}, maxTicks={}", tests.size(), filter, maxTicks);
        return tests;
    }

    private static void invoke(Method method, GameTestHelper helper) {
        try {
            method.invoke(null, helper);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot invoke GameTest " + method, exception);
        }
    }
}
