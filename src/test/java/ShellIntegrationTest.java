import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class ShellIntegrationTest {
    private static final boolean WINDOWS = File.separatorChar == '\\';
    private static final Path JAVA = Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "java.exe" : "java");
    private static final Path SHELL_CLASSES = classes(Main.class);
    private static final Path TEST_CLASSES = classes(ShellFixture.class);

    @TempDir
    Path work;

    @Test
    void runsBuiltinsAndChangesTheChildWorkingDirectory() throws Exception {
        Path nested = Files.createDirectories(work.resolve("nested space")).toRealPath();
        var result = run("type echo\ncd 'nested space'\npwd\n" + fixture("cwd")
                + "\ncd ..\npwd\ncd\npwd\ncd ~\npwd\ncd missing\nexit\necho skipped\n");
        assertEquals(List.of("echo is a shell builtin", nested.toString(), nested.toString(),
                work.toRealPath().toString(), nested.toString(), nested.toString()), result.lines());
        assertTrue(result.err().contains("cd: missing: No such file or directory"));
    }

    @Test
    void looksUpRelativePathEntriesAndExecutesAQuotedRelativeProgram() throws Exception {
        Path nested = Files.createDirectories(work.resolve("nested space"));
        Path bin = Files.createDirectories(nested.resolve("local bin"));
        String executable = WINDOWS ? "named tool.exe" : "named tool";
        Path tool = bin.resolve(executable);
        String expected;
        if (WINDOWS) {
            Files.copy(Path.of(System.getenv("SystemRoot"), "System32", "where.exe"), tool);
            expected = tool.toString();
        } else {
            Files.writeString(tool, "#!/bin/sh\nprintf '%s\\n' \"$1\"\n");
            assertTrue(tool.toFile().setExecutable(true));
            expected = "argument";
        }
        String arguments = WINDOWS ? " /r . " + quote(executable) : " argument";
        var result = run("cd 'nested space'\ntype " + quote(executable) + "\n" + quote(executable)
                + arguments + "\n" + quote("./local bin/" + executable) + arguments + "\n",
                bin.getFileName().toString());
        assertEquals(List.of(executable + " is " + tool, expected, expected), result.lines());
        assertEquals("", result.err());
    }

    @Test
    void passesQuotedAndEmptyArgumentsToExternalPrograms() throws Exception {
        var result = run(fixture("args") + " '' a' b'\" c\" '|' \"2>\" \\& a\\ b '\\q' \"\\q\"\n");
        assertEquals(List.of("[]", "[a b c]", "[|]", "[2>]", "[&]", "[a b]", "[\\q]", "[\\q]"),
                result.lines());
        assertEquals("", result.err());
    }

    @Test
    void recoversFromInvalidCommandsSyntaxAndIoErrors() throws Exception {
        var result = run("missing-command\n'broken\npwd | | pwd\necho >\necho & echo\n"
                + "echo > .\ntype missing-command\necho alive\n");
        assertEquals(List.of("alive"), result.lines());
        assertTrue(result.err().contains("missing-command: command not found"));
        assertTrue(result.err().contains("syntax error: unterminated quote"));
        assertEquals(4, result.err().lines().filter(line -> line.startsWith("syntax error:")).count());
        assertTrue(result.err().contains("shell:"));
        assertTrue(result.err().contains("missing-command: not found"));
    }

    @Test
    void redirectsBothStreamsAndAppendsRelativeToTheCurrentDirectory() throws Exception {
        Path nested = Files.createDirectory(work.resolve("nested"));
        var result = run("cd nested\n" + fixture("both") + " one 1>'logs/out file' 2>err\n"
                + fixture("both") + " two >>'logs/out file' 2>>err\n"
                + "echo three >discarded >>'logs/out file'\n"
                + "cd missing 2>>err\nmissing-command >empty 2>>err\npwd >pwd.txt\n");
        assertEquals(List.of("stdout one", "stdout two", "three"), Files.readAllLines(nested.resolve("logs/out file")));
        assertEquals(List.of("stderr one", "stderr two", "cd: missing: No such file or directory",
                "missing-command: command not found"), Files.readAllLines(nested.resolve("err")));
        assertEquals(0, Files.size(nested.resolve("discarded")));
        assertEquals(0, Files.size(nested.resolve("empty")));
        assertEquals(List.of(nested.toRealPath().toString()), Files.readAllLines(nested.resolve("pwd.txt")));
        assertFalse(Files.exists(work.resolve("err")));
        assertEquals("", result.err());
    }

    @Test
    void streamsLargePipelinesAndHandlesEarlyReaders() throws Exception {
        var result = run(fixture("flood") + " 256|" + fixture("copy") + "|" + fixture("count") + "\n"
                + fixture("flood") + " 1000000|" + fixture("one") + "\n"
                + fixture("flood") + " 1000000|echo builtin\n"
                + fixture("flood") + " 1000000|missing-stage|" + fixture("count") + "\n");
        assertEquals(List.of("2097152", "120", "builtin", "0"), result.lines());
        assertEquals("missing-stage: command not found", result.err().strip());
    }

    @Test
    void connectsBuiltinOutputAndKeepsPipelineStateAndRedirectsLocal() throws Exception {
        Files.createDirectory(work.resolve("nested"));
        var result = run("echo abc|" + fixture("copy") + "|" + fixture("count") + "\n"
                + fixture("both") + " first 2>first.err|" + fixture("copy") + " 2>last.err\n"
                + "echo file>redirected|" + fixture("count") + "\n"
                + fixture("both") + " redirected >external 2>external.err|" + fixture("count") + "\n"
                + "exit|" + fixture("count") + "\ncd nested|pwd\npwd\n");
        assertEquals(List.of("4", "stdout first", "0", "0", "0",
                work.toRealPath().toString(), work.toRealPath().toString()), result.lines());
        assertEquals(List.of("stderr first"), Files.readAllLines(work.resolve("first.err")));
        assertEquals(0, Files.size(work.resolve("last.err")));
        assertEquals(List.of("file"), Files.readAllLines(work.resolve("redirected")));
        assertEquals(List.of("stdout redirected"), Files.readAllLines(work.resolve("external")));
        assertEquals(List.of("stderr redirected"), Files.readAllLines(work.resolve("external.err")));
        assertEquals("", result.err());
    }

    @Test
    void keepsBackgroundPipelineUntilEveryStageExitsAndReusesJobNumber() throws Exception {
        try (Session session = start(null)) {
            String first = fixture("hold") + " process.pid release|" + fixture("args") + " downstream-finished";
            session.send(first + " &\n");
            Path pidFile = work.resolve("process.pid");
            long pid = session.awaitPid(pidFile);
            session.awaitOutput("[downstream-finished]");
            session.send("jobs >running\n");
            session.awaitOutput("$ ", 3);
            assertTrue(Files.readString(work.resolve("running")).contains("[1]+  Running"));
            assertTrue(ProcessHandle.of(pid).orElseThrow().isAlive());
            Files.createFile(work.resolve("release"));
            awaitStopped(pid);
            session.send("jobs\necho next &\n");
            session.awaitOutput("[1]\n");
            session.awaitOutput("Done", 2);
            var result = session.finish();
            assertEquals(1, result.out().lines().filter(line -> line.contains("Done") && line.contains("process.pid")).count());
            assertTrue(result.out().contains("[1]+  Done"));
            assertEquals("", result.err());
        }
    }

    @Test
    void reportsRunningAndFinishedJobsInJobNumberOrder() throws Exception {
        try (Session session = start(null)) {
            for (int job = 1; job <= 3; job++) {
                session.send(fixture("hold") + " job" + job + ".pid release" + job + " &\n");
            }
            long middle = session.awaitPid(work.resolve("job2.pid"));
            session.awaitOutput("$ ", 4);
            Files.createFile(work.resolve("release2"));
            awaitStopped(middle);
            session.send("jobs >status\n");
            session.awaitOutput("$ ", 5);
            var status = Files.readAllLines(work.resolve("status"));
            assertEquals(3, status.size());
            assertTrue(status.get(0).startsWith("[1]   Running"));
            assertTrue(status.get(1).startsWith("[2]-  Done"));
            assertTrue(status.get(2).startsWith("[3]+  Running"));
            assertEquals("", session.finish().err());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closesBackgroundStdinAndStopsLivePipelineStages(boolean explicitExit) throws Exception {
        Files.createDirectory(work.resolve("nested space"));
        try (Session session = start(null)) {
            session.send("cd &\npwd\n" + fixture("one") + " >background &\n"
                    + fixture("hold") + " upstream.pid release|" + fixture("hold") + " downstream.pid release &\n");
            session.awaitOutput(work.toRealPath().toString());
            long upstream = session.awaitPid(work.resolve("upstream.pid"));
            long downstream = session.awaitPid(work.resolve("downstream.pid"));
            session.awaitFileContent(work.resolve("background"), "-1");
            if (explicitExit) {
                session.send("exit\n");
            }
            var result = session.finish();
            assertTrue(result.out().contains("[1]+  Done"));
            assertFalse(ProcessHandle.of(upstream).map(ProcessHandle::isAlive).orElse(false));
            assertFalse(ProcessHandle.of(downstream).map(ProcessHandle::isAlive).orElse(false));
            assertEquals("", result.err());
        }
    }

    @Test
    void stopsStagesThatStartedBeforeAPipelineRedirectionFailed() throws Exception {
        try (Session session = start(null)) {
            session.send(fixture("hold") + " stray.pid release >started|echo > .\necho recovered\n");
            session.awaitOutput("recovered");
            assertTrue(Files.exists(work.resolve("started")));
            try (var children = session.process().descendants()) {
                // Windows may also create a console host for the shell process.
                var runningJava = children.filter(ProcessHandle::isAlive).map(ProcessHandle::info)
                        .map(ProcessHandle.Info::command).flatMap(java.util.Optional::stream)
                        .map(Path::of).map(Path::getFileName).map(Path::toString)
                        .filter(JAVA.getFileName().toString()::equalsIgnoreCase).toList();
                assertTrue(runningJava.isEmpty(), () -> "pipeline left children running: " + runningJava);
            }
            assertTrue(session.finish().err().contains("shell:"));
        }
    }

    @Test
    void preservesForegroundStdinAndHandlesBlankInputAndEof() throws Exception {
        assertEquals("$ ", run("").out());
        assertEquals("$ $ $ ", run("\n \t \n").out());
        var result = run(fixture("stdin") + " 5\nhello" + "echo final");
        assertEquals(List.of("hello", "final"), result.lines());
        assertEquals("", result.err());
    }

    private Result run(String input) throws Exception {
        return run(input, null);
    }

    private static void awaitStopped(long pid) throws Exception {
        var process = ProcessHandle.of(pid);
        if (process.isPresent()) {
            process.get().onExit().get(5, TimeUnit.SECONDS);
        }
    }

    private Result run(String input, String path) throws Exception {
        try (Session session = start(path)) {
            session.send(input);
            return session.finish();
        }
    }

    private Session start(String path) throws IOException {
        Path out = Files.createTempFile(work, "shell-out", ".txt");
        Path err = Files.createTempFile(work, "shell-err", ".txt");
        var builder = new ProcessBuilder(JAVA.toString(), "-cp", SHELL_CLASSES.toString(), "Main")
                .directory(work.toFile()).redirectOutput(out.toFile()).redirectError(err.toFile());
        builder.environment().put("HOME", work.resolve("nested space").toString());
        if (path != null) {
            builder.environment().put("PATH", path);
        }
        return new Session(builder.start(), out, err);
    }

    private static Path classes(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (java.net.URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    private static String fixture(String mode) {
        return quote(JAVA.toString()) + " -cp " + quote(TEST_CLASSES.toString()) + " ShellFixture " + mode;
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private record Result(String out, String err) {
        List<String> lines() {
            return out.replace("$ ", "").lines().toList();
        }
    }

    private record Session(Process process, Path out, Path err) implements AutoCloseable {
        void send(String commands) throws IOException {
            process.getOutputStream().write(commands.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
        }

        void awaitOutput(String text) throws Exception {
            awaitOutput(text, 1);
        }

        void awaitOutput(String text, int count) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            String output;
            do {
                output = Files.readString(out);
                if (output.split(java.util.regex.Pattern.quote(text), -1).length > count) {
                    return;
                }
                Thread.sleep(20);
            } while (process.isAlive() && System.nanoTime() < deadline);
            fail("Missing output " + text + " in " + output + "\n" + Files.readString(err));
        }

        long awaitPid(Path file) throws Exception {
            return Long.parseLong(awaitFileContent(file, null));
        }

        String awaitFileContent(Path file, String expected) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            do {
                if (Files.exists(file)) {
                    String text = Files.readString(file).strip();
                    if (!text.isEmpty() && (expected == null || text.equals(expected))) {
                        return text;
                    }
                }
                Thread.sleep(20);
            } while (process.isAlive() && System.nanoTime() < deadline);
            return fail("Missing content in " + file + "\n" + Files.readString(out) + Files.readString(err));
        }

        Result finish() throws Exception {
            process.getOutputStream().close();
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "shell did not exit");
            String error = Files.readString(err);
            assertEquals(0, process.exitValue(), error);
            return new Result(Files.readString(out), error);
        }

        @Override
        public void close() {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }
}
