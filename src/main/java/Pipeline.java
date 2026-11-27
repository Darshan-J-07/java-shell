import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

final class Pipeline {
    private record Stage(CommandLine.Command command, Path executable) {}

    private final Shell shell;
    private final Path directory;
    private final Executables executables;
    private final OutputStream stdout;
    private final OutputStream stderr;
    private final List<Process> processes = new ArrayList<>();
    private final List<Thread> writers = new ArrayList<>();

    Pipeline(Shell shell, Path directory, Executables executables, OutputStream stdout, OutputStream stderr) {
        this.shell = shell;
        this.directory = directory;
        this.executables = executables;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    void start(CommandLine line) throws IOException {
        List<Stage> stages = new ArrayList<>();
        for (var command : line.commands()) {
            String name = command.arguments().getFirst();
            stages.add(new Stage(command, Shell.isBuiltin(name) ? null : executables.find(name, directory)));
        }

        byte[] pendingInput = null;
        int index = 0;
        while (index < stages.size()) {
            Stage stage = stages.get(index);
            if (stage.executable() == null) {
                boolean hasNext = index + 1 < stages.size();
                boolean inShell = stages.size() == 1 && !line.background();
                pendingInput = runInProcess(stage.command(), hasNext, inShell);
                index++;
            } else {
                // A native pipe gives the upstream process a broken pipe when its reader exits.
                int end = index + 1;
                while (end < stages.size() && stages.get(end).executable() != null
                        && !stages.get(end - 1).command().redirectsOutput()) {
                    end++;
                }
                startExternal(stages, index, end, !line.background(), pendingInput);
                pendingInput = null;
                index = end;
            }
        }
    }

    private byte[] runInProcess(CommandLine.Command command, boolean hasNext, boolean inShell)
            throws IOException {
        ByteArrayOutputStream buffer = hasNext ? new ByteArrayOutputStream() : null;
        try (var out = new RedirectFiles.Destination(hasNext ? buffer : stdout);
             var err = new RedirectFiles.Destination(stderr)) {
            for (var redirect : command.redirects()) {
                (redirect.error() ? err : out).redirect(directory, redirect);
            }
            String name = command.arguments().getFirst();
            if (Shell.isBuiltin(name)) {
                shell.builtin(command.arguments(), directory, inShell, out.stream(), err.stream());
            } else {
                Shell.print(err.stream(), name + ": command not found");
            }
        }
        return buffer == null ? null : buffer.toByteArray();
    }

    private void startExternal(List<Stage> stages, int start, int end, boolean foreground, byte[] input)
            throws IOException {
        List<ProcessBuilder> builders = new ArrayList<>();
        for (int index = start; index < end; index++) {
            Stage stage = stages.get(index);
            var arguments = new ArrayList<>(stage.command().arguments());
            // Unix searches the inherited PATH in the child's directory and preserves argv[0].
            // Windows executable lookup can depend on the JVM's own working directory.
            if (File.separatorChar == '\\') {
                arguments.set(0, stage.executable().toString());
            }
            ProcessBuilder builder = new ProcessBuilder(arguments).directory(directory.toFile());
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            if (index == 0 && foreground) {
                builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            }
            if (index == stages.size() - 1) {
                builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }
            RedirectFiles.configure(builder, directory, stage.command().redirects());
            builders.add(builder);
        }

        List<Process> started = ProcessBuilder.startPipeline(builders);
        processes.addAll(started);
        if (start != 0 || !foreground) {
            OutputStream childInput = started.getFirst().getOutputStream();
            if (input == null || input.length == 0) {
                childInput.close();
            } else {
                writers.add(Thread.ofVirtual().name("shell-pipe-writer").start(() -> {
                    try (childInput) {
                        childInput.write(input);
                    } catch (IOException e) {
                        // A downstream process can exit without consuming its entire input.
                    }
                }));
            }
        }
        if (end < stages.size() && !stages.get(end - 1).command().redirectsOutput()) {
            // The following in-process command never reads stdin.
            started.getLast().getInputStream().close();
        }
    }

    boolean isRunning() {
        return processes.stream().anyMatch(Process::isAlive) || writers.stream().anyMatch(Thread::isAlive);
    }

    String pidSuffix() {
        return processes.isEmpty() ? "" : " " + processes.getLast().pid();
    }

    void await() throws InterruptedException {
        for (Process process : processes) {
            process.waitFor();
        }
        for (Thread writer : writers) {
            writer.join();
        }
    }

    void stop() {
        stopAll(List.of(this));
    }

    static void stopAll(List<Pipeline> pipelines) {
        List<Process> running = pipelines.stream().flatMap(pipeline -> pipeline.processes.stream())
                .filter(Process::isAlive).toList();
        // Snapshot descendants while their parents still identify them.
        List<ProcessHandle> descendants = running.stream().flatMap(Process::descendants).distinct().toList();
        descendants.forEach(ProcessHandle::destroy);
        for (Process process : running) {
            if (process.supportsNormalTermination()) {
                process.toHandle().destroy();
            } else {
                process.destroyForcibly();
            }
        }
        var exits = Stream.concat(running.stream().map(Process::onExit),
                        descendants.stream().map(ProcessHandle::onExit))
                .toArray(CompletableFuture<?>[]::new);
        var allExited = CompletableFuture.allOf(exits);
        waitForExit(allExited, 250);
        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        running.stream().filter(Process::isAlive).forEach(process -> process.toHandle().destroyForcibly());
        waitForExit(allExited, 1_000);
        for (Pipeline pipeline : pipelines) {
            for (Thread writer : pipeline.writers) {
                writer.interrupt();
            }
        }
    }

    private static void waitForExit(CompletableFuture<Void> allExited, long milliseconds) {
        try {
            allExited.get(milliseconds, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            // Cleanup will force surviving processes after the grace period.
        }
    }
}
