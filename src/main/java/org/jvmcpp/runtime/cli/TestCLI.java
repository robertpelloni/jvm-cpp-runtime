package org.jvmcpp.runtime.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Automated test suite for the jvm-cpp-runtime CLI runner (org.jvmcpp.runtime.cli.Main).
 * Runs the CLI in separate Java sub-processes to accurately validate command-line option
 * dispatching, stream captures, exit code signaling, version reporting, info metrics display,
 * and help output without relying on deprecated SecurityManager APIs.
 */
public class TestCLI {

    private static int assertionsCount = 0;

    private static void assertTrue(boolean condition, String message) {
        assertionsCount++;
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        assertionsCount++;
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Assertion failed: " + message + " (expected: " + expected + ", actual: " + actual + ")");
    }

    private static ProcessResult runProcess(String... args) {
        try {
            List<String> command = new ArrayList<>();
            command.add("java");
            command.add("-cp");
            command.add(".");
            command.add("org.jvmcpp.runtime.cli.Main");
            command.addAll(Arrays.asList(args));

            ProcessBuilder pb = new ProcessBuilder(command);
            Process proc = pb.start();

            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();

            Thread outThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        out.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
            });

            Thread errThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        err.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
            });

            outThread.start();
            errThread.start();

            int exitCode = proc.waitFor();
            outThread.join();
            errThread.join();

            return new ProcessResult(exitCode, out.toString(), err.toString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to spawn CLI process", e);
        }
    }

    private static class ProcessResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    public static void main(String[] args) {
        System.out.println("Starting TestCLI suite...");

        testHelpOption();
        testVersionOption();
        testInfoOption();
        testInvalidOption();
        testNoArgsOption();
        testTestOption();
        testIntegrationOption();

        System.out.println("TestCLI suite passed successfully! Total assertions: " + assertionsCount);
    }

    private static void testHelpOption() {
        ProcessResult res = runProcess("--help");
        assertEquals(0, res.exitCode, "Help option should exit cleanly with 0");
        assertTrue(res.stdout.contains("jvm-cpp-runtime CLI runner"), "Help stdout should contain runner description");
        assertTrue(res.stdout.contains("--test"), "Help stdout should document --test");
        assertTrue(res.stdout.contains("--version"), "Help stdout should document --version");
        assertTrue(res.stderr.isEmpty(), "Help stderr should be empty");

        ProcessResult resShort = runProcess("-h");
        assertEquals(0, resShort.exitCode, "-h should exit cleanly with 0");
        assertTrue(resShort.stdout.contains("jvm-cpp-runtime CLI runner"), "-h stdout should contain description");
    }

    private static void testNoArgsOption() {
        ProcessResult res = runProcess();
        assertEquals(0, res.exitCode, "No args should display help and exit with 0");
        assertTrue(res.stdout.contains("jvm-cpp-runtime CLI runner"), "No args stdout should contain description");
    }

    private static void testVersionOption() {
        ProcessResult resLong = runProcess("--version");
        assertEquals(0, resLong.exitCode, "--version should exit cleanly with 0");
        assertTrue(resLong.stdout.contains("jvm-cpp-runtime version 0.2.0"), "--version should print current version");

        ProcessResult resShort = runProcess("-v");
        assertEquals(0, resShort.exitCode, "-v should exit cleanly with 0");
        assertTrue(resShort.stdout.contains("jvm-cpp-runtime version 0.2.0"), "-v should print current version");
    }

    private static void testInfoOption() {
        ProcessResult resLong = runProcess("--info");
        assertEquals(0, resLong.exitCode, "--info should exit cleanly with 0");
        assertTrue(resLong.stdout.contains("JVM-CPP-RUNTIME INFO:"), "--info header should be present");
        assertTrue(resLong.stdout.contains("Java Version:"), "--info should contain Java Version");
        assertTrue(resLong.stdout.contains("Runtime Version:      0.2.0"), "--info should contain Runtime Version");
        assertTrue(resLong.stdout.contains("Active Allocations:"), "--info should contain Active Allocations");

        ProcessResult resShort = runProcess("-i");
        assertEquals(0, resShort.exitCode, "-i should exit cleanly with 0");
        assertTrue(resShort.stdout.contains("JVM-CPP-RUNTIME INFO:"), "-i header should be present");
    }

    private static void testInvalidOption() {
        ProcessResult res = runProcess("--unknown-flag-12345");
        assertEquals(1, res.exitCode, "Invalid flag should trigger exit code 1");
        assertTrue(res.stderr.contains("Unknown option: --unknown-flag-12345"), "Stderr should report unknown option");
        assertTrue(res.stdout.contains("jvm-cpp-runtime CLI runner"), "Stdout should display help on unknown option");
    }

    private static void testTestOption() {
        ProcessResult res = runProcess("--test");
        assertEquals(0, res.exitCode, "--test should exit cleanly with 0");
        assertTrue(res.stdout.contains("[CLI] Running runtime test harness..."), "Stdout should indicate starting runtime test harness");
        assertTrue(res.stdout.contains("All test suites passed successfully!"), "Stdout should indicate tests passed");
    }

    private static void testIntegrationOption() {
        ProcessResult res = runProcess("--integration");
        assertEquals(0, res.exitCode, "--integration should exit cleanly with 0");
        assertTrue(res.stdout.contains("[CLI] Running integration test suite..."), "Stdout should indicate starting integration test suite");
        assertTrue(res.stdout.contains("ALL INTEGRATION TESTS PASSED"), "Stdout should indicate integration tests passed");
    }
}
