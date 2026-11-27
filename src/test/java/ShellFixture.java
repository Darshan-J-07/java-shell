import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class ShellFixture {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "args" -> {
                for (int i = 1; i < args.length; i++) {
                    System.out.println("[" + args[i] + "]");
                }
            }
            case "cwd" -> System.out.println(Path.of("").toAbsolutePath().toRealPath());
            case "both" -> {
                System.out.println("stdout " + args[1]);
                System.err.println("stderr " + args[1]);
            }
            case "copy" -> System.in.transferTo(System.out);
            case "count" -> {
                long count = System.in.transferTo(java.io.OutputStream.nullOutputStream());
                System.out.println(count);
            }
            case "one" -> System.out.println(System.in.read());
            case "stdin" -> {
                try (var input = new FileInputStream(FileDescriptor.in)) {
                    System.out.println(new String(input.readNBytes(Integer.parseInt(args[1])),
                            StandardCharsets.UTF_8));
                }
            }
            case "flood" -> {
                int blocks = Integer.parseInt(args[1]);
                byte[] data = new byte[8192];
                Arrays.fill(data, (byte) 'x');
                try (var out = new FileOutputStream(FileDescriptor.out)) {
                    for (int i = 0; i < blocks; i++) {
                        out.write(data);
                    }
                } catch (IOException e) {
                    // Early-reader integration tests close the native pipe on purpose.
                }
            }
            case "hold" -> {
                Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
                Path release = Path.of(args[2]);
                while (!Files.exists(release)) {
                    Thread.sleep(20);
                }
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}
