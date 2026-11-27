import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

final class RedirectFiles {
    private RedirectFiles() {}

    static void configure(ProcessBuilder builder, Path directory, List<CommandLine.Redirect> redirects)
            throws IOException {
        for (int i = 0; i < redirects.size(); i++) {
            var redirect = redirects.get(i);
            boolean superseded = redirects.subList(i + 1, redirects.size()).stream()
                    .anyMatch(later -> later.error() == redirect.error());
            if (superseded) {
                // A later redirect changes the destination, but earlier files are still opened.
                open(path(directory, redirect), redirect.append()).close();
            } else {
                var file = path(directory, redirect).toFile();
                var destination = redirect.append()
                        ? ProcessBuilder.Redirect.appendTo(file) : ProcessBuilder.Redirect.to(file);
                if (redirect.error()) {
                    builder.redirectError(destination);
                } else {
                    builder.redirectOutput(destination);
                }
            }
        }
    }

    private static Path path(Path directory, CommandLine.Redirect redirect) throws IOException {
        if (redirect.file().isEmpty()) {
            throw new IOException("empty redirection filename");
        }
        Path path = directory.resolve(redirect.file());
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        return path;
    }

    private static OutputStream open(Path path, boolean append) throws IOException {
        var mode = append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING;
        return Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, mode);
    }

    static final class Destination implements AutoCloseable {
        private OutputStream stream;
        private OutputStream file;

        Destination(OutputStream stream) {
            this.stream = stream;
        }

        OutputStream stream() {
            return stream;
        }

        void redirect(Path directory, CommandLine.Redirect redirect) throws IOException {
            close();
            file = open(path(directory, redirect), redirect.append());
            stream = file;
        }

        @Override
        public void close() throws IOException {
            if (file != null) {
                OutputStream owned = file;
                file = null;
                owned.close();
            }
        }
    }
}
