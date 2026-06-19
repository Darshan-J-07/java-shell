import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
            
            String redirectOutFile = null;
            String redirectErrFile = null;
            boolean appendOut = false;
            boolean appendErr = false;
            
            for (int i = 0; i < parts.size(); i++) {
                String token = parts.get(i);
                if (token.equals(">>") || token.equals("1>>")) {
                    if (i + 1 < parts.size()) {
                        redirectOutFile = parts.get(i + 1);
                        appendOut = true;
                        parts.remove(i + 1);
                        parts.remove(i);
                        i--;
                    }
                } else if (token.equals(">") || token.equals("1>")) {
                    if (i + 1 < parts.size()) {
                        redirectOutFile = parts.get(i + 1);
                        appendOut = false;
                        parts.remove(i + 1);
                        parts.remove(i);
                        i--;
                    }
                } else if (token.equals("2>>")) {
                    if (i + 1 < parts.size()) {
                        redirectErrFile = parts.get(i + 1);
                        appendErr = true;
                        parts.remove(i + 1);
                        parts.remove(i);
                        i--;
                    }
                } else if (token.equals("2>")) {
                    if (i + 1 < parts.size()) {
                        redirectErrFile = parts.get(i + 1);
                        appendErr = false;
                        parts.remove(i + 1);
                        parts.remove(i);
                        i--;
                    }
                }
            }
            
            if (parts.isEmpty()) continue;
            String cmd = parts.get(0);
            
            if (builtins.contains(cmd)) {
                if (redirectOutFile != null) {
                    File f = new File(redirectOutFile);
                    if (f.getParentFile() != null) f.getParentFile().mkdirs();
                    if (!appendOut) {
                        Files.writeString(f.toPath(), "");
                    } else if (!f.exists()) {
                        f.createNewFile();
                    }
                }
                if (redirectErrFile != null) {
                    File f = new File(redirectErrFile);
                    if (f.getParentFile() != null) f.getParentFile().mkdirs();
                    if (!appendErr) {
                        Files.writeString(f.toPath(), "");
                    } else if (!f.exists()) {
                        f.createNewFile();
                    }
                }
            }
            
            if (cmd.equals("type")) {
                if (parts.size() < 2) continue;
                String targetCmd = parts.get(1);
                
                if (builtins.contains(targetCmd)) {
                    output(targetCmd + " is a shell builtin", redirectOutFile, appendOut);
                    continue;
                }
                
                String execPath = getExecutablePath(targetCmd);
                if (execPath != null) {
                    output(targetCmd + " is " + execPath, redirectOutFile, appendOut);
                } else {
                    System.out.println(targetCmd + ": not found");
                }
            } 
            else if (cmd.equals("pwd")) {
                output(currentDir, redirectOutFile, appendOut);
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
                output(String.join(" ", parts.subList(1, parts.size())), redirectOutFile, appendOut);
            } 
            else {
                String execPath = getExecutablePath(cmd);
                if (execPath != null) {
                    ProcessBuilder pb = new ProcessBuilder(parts);
                    pb.directory(new File(currentDir));
                    
                    if (redirectOutFile != null) {
                        File f = new File(redirectOutFile);
                        if (f.getParentFile() != null) f.getParentFile().mkdirs();
                        if (appendOut) {
                            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(f));
                        } else {
                            pb.redirectOutput(f);
                        }
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }
                    
                    if (redirectErrFile != null) {
                        File f = new File(redirectErrFile);
                        if (f.getParentFile() != null) f.getParentFile().mkdirs();
                        if (appendErr) {
                            pb.redirectError(ProcessBuilder.Redirect.appendTo(f));
                        } else {
                            pb.redirectError(f);
                        }
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }
                    
                    Process p = pb.start();
                    p.waitFor();
                } else {
                    System.out.println(cmd + ": command not found");
                }
            }
        }
    }

    private static void output(String content, String redirectFile, boolean append) throws Exception {
        if (redirectFile != null) {
            File f = new File(redirectFile);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            if (append) {
                Files.writeString(f.toPath(), content + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(f.toPath(), content + "\n");
            }
        } else {
            System.out.println(content);
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