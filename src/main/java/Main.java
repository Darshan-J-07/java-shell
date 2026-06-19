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
            
            List<String> parts = parseArguments(s);
            if (parts.isEmpty()) continue;
            
            String cmd = parts.get(0);
            
            if (cmd.equals("type")) {
                if (parts.size() < 2) continue;
                String targetCmd = parts.get(1);
                
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
                if (parts.size() < 2) continue;
                String dir = parts.get(1);
                
                if (dir.startsWith("~")) {
                    dir = dir.replace("~", System.getenv("HOME"));
                }
                
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
            else if (cmd.equals("echo")) {
                System.out.println(String.join(" ", parts.subList(1, parts.size())));
            } 
            else {
                String execPath = getExecutablePath(cmd);
                if (execPath != null) {
                    ProcessBuilder pb = new ProcessBuilder(parts);
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

    private static List<String> parseArguments(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean isEscaped = false;
        boolean inArg = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (isEscaped) {
                currentArg.append(c);
                inArg = true;
                isEscaped = false;
            } else if (inSingleQuote) {
                if (c == '\'') {
                    inSingleQuote = false;
                } else {
                    currentArg.append(c);
                }
                inArg = true;
            } else if (inDoubleQuote) {
                if (c == '\\' && i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == '"' || next == '\\' || next == '$' || next == '`' || next == '\n') {
                        currentArg.append(next);
                        i++;
                    } else {
                        currentArg.append(c);
                    }
                    inArg = true;
                } else if (c == '"') {
                    inDoubleQuote = false;
                    inArg = true;
                } else {
                    currentArg.append(c);
                    inArg = true;
                }
            } else {
                if (c == '\\') {
                    isEscaped = true;
                    inArg = true;
                } else if (c == '\'') {
                    inSingleQuote = true;
                    inArg = true;
                } else if (c == '"') {
                    inDoubleQuote = true;
                    inArg = true;
                } else if (c == ' ') {
                    if (inArg) {
                        args.add(currentArg.toString());
                        currentArg.setLength(0);
                        inArg = false;
                    }
                } else {
                    currentArg.append(c);
                    inArg = true;
                }
            }
        }
        if (inArg) {
            args.add(currentArg.toString());
        }
        return args;
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