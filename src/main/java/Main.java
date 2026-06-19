import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        List<String> builtins = Arrays.asList("echo", "exit", "type", "pwd", "cd");
        String currentDir = System.getProperty("user.dir");
        
        while (true) {
            System.out.print("$ ");
            String s = sc.nextLine();
            if (s.isEmpty()) continue;
            
            String[] parts = s.split(" ");
            String cmd = parts[0];
            
            if (s.startsWith("type ")) {
                String targetCmd = s.substring(5).trim();
                
                if (builtins.contains(targetCmd)) {
                    System.out.println(targetCmd + " is a shell builtin");
                    continue;
                }
                
                String execPath = getExecutablePath(targetCmd);
                if (execPath != null) {
                    System.out.println(targetCmd + " is " + execPath);
                } else {
                    System.out.println(targetCmd + ": not found");
                }
            } 
            else if (cmd.equals("pwd")) {
                System.out.println(currentDir);
            }
            else if (cmd.equals("cd")) {
                String dir = s.substring(3).trim();
                Path targetPath = Path.of(currentDir).resolve(dir).normalize();
                
                if (Files.isDirectory(targetPath)) {
                    currentDir = targetPath.toAbsolutePath().toString();
                } else {
                    System.out.println("cd: " + dir + ": No such file or directory");
                }
            }
            else if (cmd.equals("exit")) {
                break;
            } 
            else if (s.startsWith("echo ")) {
                System.out.println(s.substring(5));
            } 
            else {
                String execPath = getExecutablePath(cmd);
                if (execPath != null) {
                    List<String> commandList = new ArrayList<>(Arrays.asList(parts));
                    
                    ProcessBuilder pb = new ProcessBuilder(commandList);
                    pb.directory(new File(currentDir));
                    pb.inheritIO();
                    Process p = pb.start();
                    p.waitFor();
                } else {
                    System.out.println(cmd + ": command not found");
                }
            }
        }
    }

    private static String getExecutablePath(String cmd) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] paths = pathEnv.split(File.pathSeparator);
            for (String dir : paths) {
                Path fullPath = Path.of(dir, cmd);
                if (Files.isRegularFile(fullPath) && Files.isExecutable(fullPath)) {
                    return fullPath.toString();
                }
            }
        }
        return null;
    }
}