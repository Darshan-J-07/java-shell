import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

final class Executables {
    private static final boolean WINDOWS = File.separatorChar == '\\';

    private final String path;
    private final String[] extensions;

    Executables(String path, String pathExt) {
        this.path = path;
        extensions = WINDOWS && pathExt != null ? pathExt.split(";") : new String[0];
    }

    Path find(String name, Path directory) {
        if (name.isEmpty()) {
            return null;
        }
        Path command = Path.of(name);
        if (name.indexOf('/') >= 0 || (WINDOWS && name.indexOf('\\') >= 0)
                || command.getRoot() != null) {
            return findFile(directory.resolve(command));
        }
        if (path != null) {
            for (String entry : path.split(Pattern.quote(File.pathSeparator), -1)) {
                Path executable = findFile(directory.resolve(entry).resolve(command));
                if (executable != null) {
                    return executable;
                }
            }
        }
        return null;
    }

    private Path findFile(Path candidate) {
        if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
            return candidate;
        }
        for (String suffix : extensions) {
            Path withExtension = candidate.resolveSibling(candidate.getFileName()
                    + suffix.toLowerCase(Locale.ROOT));
            if (Files.isRegularFile(withExtension) && Files.isExecutable(withExtension)) {
                return withExtension;
            }
        }
        return null;
    }
}
