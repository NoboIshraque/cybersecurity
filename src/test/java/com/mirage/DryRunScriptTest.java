package com.mirage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FB-05: the GUI dry run (tools/screenshots/LiveDemoShot.java) must be runnable on Windows,
 * because DEMO.md sends presenters to it as the rehearsal check. The README's Git Bash lines
 * fed {@code $PWD} ({@code /e/...}) into a Windows classpath, so javac could not find a single
 * Mirage class, and there was no PowerShell or cmd way to run it at all.
 */
class DryRunScriptTest {

    private static final Path SCRIPT = Path.of("tools", "screenshots", "dry-run.cmd");
    private static final Path README = Path.of("tools", "screenshots", "README.md");

    @Test
    void theWindowsDryRunScriptExistsWithCrlfLineEndings() throws Exception {
        assertTrue(Files.isRegularFile(SCRIPT), SCRIPT + " exists");
        String text = Files.readString(SCRIPT, StandardCharsets.UTF_8);
        assertTrue(text.contains("\r\n"), "uses CRLF");
        assertFalse(text.replace("\r\n", "").contains("\n"), "no bare LF line endings (cmd.exe needs CRLF)");
    }

    @Test
    void theScriptBuildsCompilesAndRunsLiveDemoShotAndKeepsItsExitCode() throws Exception {
        String text = Files.readString(SCRIPT, StandardCharsets.UTF_8).toLowerCase();
        assertTrue(text.contains("mvnw.cmd"), "builds with the Maven Wrapper");
        assertTrue(text.contains("dependency:build-classpath"), "gets the runtime classpath from Maven");
        assertTrue(text.contains("javac") && text.contains("livedemoshot.java"), "compiles LiveDemoShot");
        assertTrue(text.contains("--compile-only"), "can stop before launching the demo");

        int run = text.indexOf(" livedemoshot \"");
        assertTrue(run >= 0, "runs LiveDemoShot with a screenshot folder");
        assertTrue(text.substring(run).startsWith(" livedemoshot \"%root%\\docs\\screenshots\""),
                "screenshots go to docs\\screenshots");
        String after = text.substring(run);
        assertTrue(after.contains("set \"code=%errorlevel%\""), "saves LiveDemoShot's exit code");
        assertTrue(after.contains("pause"), "keeps a double-clicked window open on a failure");
        assertTrue(after.contains("exit /b %code%"), "exits with LiveDemoShot's code");
    }

    @Test
    void theReadmeWindowsLinesNeverPutAPosixPathOnTheClasspath() throws Exception {
        String readme = Files.readString(README, StandardCharsets.UTF_8);
        assertTrue(readme.contains("dry-run.cmd"), "the README points at dry-run.cmd");
        assertTrue(readme.contains("--compile-only"), "the README documents --compile-only");

        String gitBash = block(readme, "Git Bash");
        assertTrue(gitBash.contains("pwd -W"), "Git Bash: Windows paths come from pwd -W");
        for (String line : gitBash.split("\n")) {
            if (line.contains("javac") || line.contains("java ") || line.contains("CP=")) {
                assertFalse(line.contains("$PWD") || line.contains("$OLDPWD"),
                        "Git Bash line feeds a /e/... path to Java: " + line);
            }
        }

        String powerShell = block(readme, "PowerShell");
        assertTrue(powerShell.contains("javac") && powerShell.contains("LiveDemoShot"), "PowerShell block runs it");
        assertFalse(powerShell.contains("&&"), "PowerShell 5.1 has no &&");
        assertFalse(powerShell.contains("$(cat "), "PowerShell block is not bash");
    }

    /** The first fenced code block after the first heading or label line naming {@code label}. */
    private static String block(String readme, String label) {
        int at = readme.indexOf("Live-shells dry run");
        assertTrue(at >= 0, "dry-run section");
        int label0 = readme.indexOf(label + ":", at);
        assertTrue(label0 >= 0, "a '" + label + ":' block in the dry-run section");
        int open = readme.indexOf("```", label0);
        int body = readme.indexOf('\n', open) + 1;
        int close = readme.indexOf("```", body);
        assertTrue(open >= 0 && close > body, "a code block after '" + label + ":'");
        return readme.substring(body, close);
    }
}
