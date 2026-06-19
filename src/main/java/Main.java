import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

public class Main {
    
    static class Job {
        int id;
        Process p;
        String cmd;

        Job(int id, Process p, String cmd) {
            this.id = id;
            this.p = p;
            this.cmd = cmd;
        }
    }

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        List<String> builtins = Arrays.asList("echo", "exit", "type", "pwd", "cd", "jobs");
        String currentDir = System.getProperty("user.dir");
        
        List<Job> backgroundJobs = new ArrayList<>();
        
        while (true) {
            checkAndReapJobs(backgroundJobs, false);
            
            System.out.print("$ ");
            String s = sc.nextLine();
            if (s.isEmpty()) continue;
            
            List<String> parts = parseArguments(s);
            if (parts.isEmpty()) continue;
            
            boolean isBackground = false;
            if (parts.get(parts.size() - 1).equals("&")) {
                isBackground = true;
                parts.remove(parts.size() - 1);
            }
            
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
            List<List<String>> pipelineCmds = new ArrayList<>();
            List<String> currentCmd = new ArrayList<>();
            for (String token : parts) {
                if (token.equals("|")) {
                    pipelineCmds.add(currentCmd);
                    currentCmd = new ArrayList<>();
                } else {
                    currentCmd.add(token);
                }
            }
            pipelineCmds.add(currentCmd);

            if (pipelineCmds.size() > 1) {
                currentDir = executePipeline(pipelineCmds, redirectOutFile, redirectErrFile,
                        appendOut, appendErr, isBackground, backgroundJobs, builtins, currentDir, s);
                continue;
            }
            
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
            else if (cmd.equals("jobs")) {
                checkAndReapJobs(backgroundJobs, true);
                continue;
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
                    if (isBackground) {
                        int nextJobId = 1;
                        for (Job j : backgroundJobs) {
                            if (j.id >= nextJobId) {
                                nextJobId = j.id + 1;
                            }
                        }
                        
                        System.out.println("[" + nextJobId + "] " + p.pid());
                        backgroundJobs.add(new Job(nextJobId, p, s));
                    } else {
                        p.waitFor();
                    }
                } else {
                    System.out.println(cmd + ": command not found");
                }
            }
        }
    }

    
    private static String executePipeline(
            List<List<String>> pipelineCmds,
            String redirectOutFile, String redirectErrFile,
            boolean appendOut, boolean appendErr,
            boolean isBackground,
            List<Job> backgroundJobs,
            List<String> builtins,
            String currentDir,
            String rawLine) throws Exception {

        int n = pipelineCmds.size();
        List<Process> processes = new ArrayList<>();
        List<Thread> pipeThreads = new ArrayList<>();
        List<Thread> builtinThreads = new ArrayList<>();
        java.io.InputStream prevStdout = null;

        boolean error = false;

        for (int i = 0; i < n && !error; i++) {
            List<String> pCmd = pipelineCmds.get(i);
            boolean isFirst = (i == 0);
            boolean isLast  = (i == n - 1);

            if (pCmd.isEmpty()) {
                System.err.println("syntax error: empty command in pipeline");
                error = true;
                break;
            }

            String cmdName = pCmd.get(0);

            if (builtins.contains(cmdName)) {

                final java.io.InputStream builtinStdin = prevStdout; 
                final java.io.OutputStream builtinStdout;
                final java.io.InputStream nextStdin;  

                if (isLast) {
                    if (redirectOutFile != null) {
                        File f = new File(redirectOutFile);
                        if (f.getParentFile() != null) f.getParentFile().mkdirs();
                        builtinStdout = new java.io.FileOutputStream(f, appendOut);
                        nextStdin = null;
                    } else {
                        builtinStdout = new java.io.FilterOutputStream(System.out) {
                            @Override public void close() {  }
                        };
                        nextStdin = null;
                    }
                } else {
                    java.io.PipedInputStream pis = new java.io.PipedInputStream(65536);
                    java.io.PipedOutputStream pos = new java.io.PipedOutputStream(pis);
                    builtinStdout = pos;
                    nextStdin = pis;
                }

                final List<String> finalPCmd = pCmd;
                final String finalCurrentDir = currentDir;
                final List<Job> finalBgJobs = backgroundJobs;

                Thread t = new Thread(() -> {
                    try {
                        if (builtinStdin != null) {
                            drainStream(builtinStdin);
                        }
                        executeBuiltinToStream(finalPCmd, builtinStdout, finalCurrentDir, finalBgJobs);
                    } catch (Exception e) {
                    } finally {
                        try { builtinStdout.close(); } catch (Exception e) {}
                    }
                });
                t.start();
                builtinThreads.add(t);

                prevStdout = nextStdin; 

            } else {
                String execPath = getExecutablePath(cmdName);
                if (execPath == null) {
                    System.err.println(cmdName + ": command not found");
                    if (prevStdout != null) drainStream(prevStdout);
                    error = true;
                    break;
                }

                ProcessBuilder pb = new ProcessBuilder(pCmd);
                pb.directory(new File(currentDir));
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                if (isFirst && prevStdout == null) {
                    pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                } else {
                    pb.redirectInput(ProcessBuilder.Redirect.PIPE);
                }
                if (isLast) {
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
                    }
                } else {
                    pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                }

                Process p = pb.start();
                processes.add(p);
                if (prevStdout != null) {
                    final java.io.InputStream src = prevStdout;
                    final java.io.OutputStream dst = p.getOutputStream();
                    final Process upstreamProcess = processes.size() >= 2
                            ? processes.get(processes.size() - 2)
                            : null;
                    Thread pipe = new Thread(() -> {
                        try {
                            byte[] buf = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = src.read(buf)) != -1) {
                                try {
                                    dst.write(buf, 0, bytesRead);
                                    dst.flush();
                                } catch (java.io.IOException writeEx) {
                                    if (upstreamProcess != null) upstreamProcess.destroy();
                                    break;
                                }
                            }
                        } catch (Exception e) {
                        } finally {
                            try { dst.close(); } catch (Exception e) {}
                            try { src.close(); } catch (Exception e) {}
                        }
                    });
                    pipe.setDaemon(true);
                    pipe.start();
                    pipeThreads.add(pipe);
                }

                if (!isLast) {
                    prevStdout = p.getInputStream();
                } else {
                    prevStdout = null;
                }
            }
        }

        if (!error) {
            if (isBackground && !processes.isEmpty()) {
                Process lastProcess = processes.get(processes.size() - 1);
                int nextJobId = 1;
                for (Job j : backgroundJobs) {
                    if (j.id >= nextJobId) nextJobId = j.id + 1;
                }
                System.out.println("[" + nextJobId + "] " + lastProcess.pid());
                backgroundJobs.add(new Job(nextJobId, lastProcess, rawLine));
            } else {
                for (Process p : processes) p.waitFor();
                for (Thread t : builtinThreads) t.join();
                for (Thread t : pipeThreads) t.join(500);
            }
        } else {
            for (Process p : processes) {
                try { p.destroyForcibly(); } catch (Exception e) {}
            }
        }

        return currentDir;
    }

    
    private static void drainStream(java.io.InputStream in) {
        Thread t = new Thread(() -> {
            try {
                byte[] buf = new byte[8192];
                while (in.read(buf) != -1) {}
            } catch (Exception e) {}
            finally { try { in.close(); } catch (Exception e) {} }
        });
        t.setDaemon(true);
        t.start();
    }

    private static void executeBuiltinToStream(List<String> parts, java.io.OutputStream out,
            String currentDir, List<Job> backgroundJobs) throws Exception {
        String cmd = parts.get(0);
        StringBuilder sb = new StringBuilder();

        if (cmd.equals("echo")) {
            sb.append(String.join(" ", parts.subList(1, parts.size()))).append("\n");
        } else if (cmd.equals("pwd")) {
            sb.append(currentDir).append("\n");
        } else if (cmd.equals("type")) {
            if (parts.size() >= 2) {
                String targetCmd = parts.get(1);
                if (Arrays.asList("echo", "exit", "type", "pwd", "cd", "jobs").contains(targetCmd)) {
                    sb.append(targetCmd).append(" is a shell builtin\n");
                } else {
                    String execPath = getExecutablePath(targetCmd);
                    if (execPath != null) {
                        sb.append(targetCmd).append(" is ").append(execPath).append("\n");
                    } else {
                        sb.append(targetCmd).append(": not found\n");
                    }
                }
            }
        } else if (cmd.equals("jobs")) {
            for (int k = 0; k < backgroundJobs.size(); k++) {
                Job j = backgroundJobs.get(k);
                if (j.p.isAlive()) {
                    char marker = ' ';
                    if (k == backgroundJobs.size() - 1) marker = '+';
                    else if (k == backgroundJobs.size() - 2) marker = '-';
                    sb.append(String.format("[%d]%c  %-24s%s\n", j.id, marker, "Running", j.cmd));
                }
            }
        } else if (cmd.equals("cd")) {
            if (parts.size() >= 2) {
                String dir = parts.get(1);
                if (dir.startsWith("~")) dir = dir.replace("~", System.getenv("HOME"));
                Path targetPath = Path.of(currentDir).resolve(dir).normalize();
                if (!Files.isDirectory(targetPath)) {
                    sb.append("cd: ").append(dir).append(": No such file or directory\n");
                }
            }
        } else if (cmd.equals("exit")) {
            System.exit(0);
        }

        if (sb.length() > 0) {
            out.write(sb.toString().getBytes());
            out.flush();
        }
    }

    private static void checkAndReapJobs(List<Job> backgroundJobs, boolean printRunning) {
        Iterator<Job> iterator = backgroundJobs.iterator();
        int k = 0;
        int size = backgroundJobs.size();
        
        while (iterator.hasNext()) {
            Job j = iterator.next();
            char marker = ' ';
            if (k == size - 1) {
                marker = '+';
            } else if (k == size - 2) {
                marker = '-';
            }
            
            if (j.p.isAlive()) {
                if (printRunning) {
                    System.out.printf("[%d]%c  %-24s%s\n", j.id, marker, "Running", j.cmd);
                }
            } else {
                String doneCmd = j.cmd;
                int ampIndex = doneCmd.lastIndexOf('&');
                if (ampIndex != -1) {
                    doneCmd = doneCmd.substring(0, ampIndex).trim();
                }
                System.out.printf("[%d]%c  %-24s%s\n", j.id, marker, "Done", doneCmd);
                iterator.remove();
            }
            k++;
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
