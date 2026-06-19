import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        List<String> builtins = Arrays.asList("echo", "exit", "type");
        
        while (true) {
            System.out.print("$ ");
            String s = sc.nextLine();
            
            if (s.startsWith("type ")) {
                String cmd = s.substring(5).trim();
                
                if (builtins.contains(cmd)) {
                    System.out.println(cmd + " is a shell builtin");
                    continue;
                }
                
                String pathEnv = System.getenv("PATH");
                boolean found = false;
                
                if (pathEnv != null) {
                    String[] paths = pathEnv.split(File.pathSeparator);
                    
                    for (String dir : paths) {
                        Path fullPath = Path.of(dir, cmd);
                        
                        if (Files.isRegularFile(fullPath) && Files.isExecutable(fullPath)) {
                            System.out.println(cmd + " is " + fullPath.toString());
                            found = true;
                            break;
                        }
                    }
                }
                
                if (!found) {
                    System.out.println(cmd + ": not found");
                }
            } 
            else if (s.equals("exit") || s.startsWith("exit ")) {
                break;
            } 
            else if (s.startsWith("echo ")) {
                System.out.println(s.substring(5));
            } 
            else {
                System.out.println(s + ": command not found");
            }
        }
    }
}