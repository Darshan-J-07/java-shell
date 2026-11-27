import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

final class Shell {
    private static final Set<String> BUILTINS = Set.of("echo", "exit", "type", "pwd", "cd", "jobs");

    private final Jobs jobs = new Jobs();
    private final Executables executables = new Executables(System.getenv("PATH"), System.getenv("PATHEXT"));
    private final OutputStream stdout = System.out;
    private final OutputStream stderr = System.err;
    private Path directory = Path.of("").toAbsolutePath();
    private boolean exiting;

    void run(InputStream input) throws IOException, InterruptedException {
        try {
            while (!exiting) {
                jobs.reap(stdout);
                System.out.print("$ ");
                System.out.flush();
                String line = readLine(input);
                if (line == null) {
                    break;
                }
                try {
                    execute(CommandLine.parse(line));
                } catch (CommandLine.SyntaxException e) {
                    print(stderr, "syntax error: " + e.getMessage());
                } catch (IOException | InvalidPathException e) {
                    print(stderr, "shell: " + e.getMessage());
                }
            }
        } finally {
            jobs.close();
        }
    }

    static boolean isBuiltin(String name) {
        return BUILTINS.contains(name);
    }

    private void execute(CommandLine line) throws IOException, InterruptedException {
        if (line.commands().isEmpty()) {
            return;
        }
        Pipeline pipeline = new Pipeline(this, directory, executables, stdout, stderr);
        boolean handedOff = false;
        try {
            pipeline.start(line);
            if (line.background()) {
                jobs.add(pipeline, line.display(), stdout);
                handedOff = true;
            } else {
                pipeline.await();
            }
        } finally {
            if (!handedOff && pipeline.isRunning()) {
                pipeline.stop();
            }
        }
    }

    void builtin(List<String> arguments, Path workingDirectory, boolean inShell,
                 OutputStream out, OutputStream err) throws IOException {
        List<String> operands = arguments.subList(1, arguments.size());
        switch (arguments.getFirst()) {
            case "echo" -> print(out, String.join(" ", operands));
            case "pwd" -> print(out, workingDirectory.toString());
            case "type" -> {
                if (operands.isEmpty()) {
                    print(err, "type: expected a command name");
                } else {
                    String name = operands.getFirst();
                    if (isBuiltin(name)) {
                        print(out, name + " is a shell builtin");
                    } else {
                        Path executable = executables.find(name, workingDirectory);
                        if (executable != null) {
                            print(out, name + " is " + executable);
                        } else {
                            print(err, name + ": not found");
                        }
                    }
                }
            }
            case "cd" -> changeDirectory(operands, workingDirectory, inShell, err);
            case "jobs" -> jobs.list(out);
            case "exit" -> {
                if (inShell) {
                    exiting = true;
                }
            }
            default -> throw new IllegalArgumentException("unknown builtin");
        }
    }

    private void changeDirectory(List<String> operands, Path workingDirectory, boolean inShell,
                                 OutputStream err) throws IOException {
        if (operands.size() > 1) {
            print(err, "cd: too many arguments");
            return;
        }
        String home = System.getenv("HOME");
        if (home == null || home.isEmpty()) {
            home = System.getProperty("user.home");
        }
        String argument = operands.isEmpty() ? "~" : operands.getFirst();
        String target = argument;
        if (argument.equals("~") || argument.startsWith("~/")) {
            target = home + argument.substring(1);
        }
        try {
            Path resolved = workingDirectory.resolve(target).normalize();
            if (Files.isDirectory(resolved)) {
                if (inShell) {
                    directory = resolved;
                }
                return;
            }
        } catch (InvalidPathException e) {
            print(err, "cd: " + argument + ": " + e.getReason());
            return;
        }
        print(err, "cd: " + target + ": No such file or directory");
    }

    static void print(OutputStream destination, String message) throws IOException {
        destination.write((message + "\n").getBytes(StandardCharsets.UTF_8));
        destination.flush();
    }

    private static String readLine(InputStream input) throws IOException {
        var line = new ByteArrayOutputStream();
        int next;
        while ((next = input.read()) != -1 && next != '\n') {
            line.write(next);
        }
        if (next == -1 && line.size() == 0) {
            return null;
        }
        String text = line.toString(StandardCharsets.UTF_8);
        return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
    }
}
