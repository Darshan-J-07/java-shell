import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        // Read exactly one command line at a time so a foreground child can read stdin.
        try (var input = new FileInputStream(FileDescriptor.in)) {
            new Shell().run(input);
        } catch (IOException e) {
            System.err.println("shell: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
