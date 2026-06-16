import java.util.Scanner;
public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        while(true){
        System.out.print("$ ");
        String s = sc.nextLine();
        if(s.startsWith("type")){
            if(s.equals("type echo")){
                System.out.println("echo is a shell builtin");
                continue;
            }
            if(s.equals("type exit")){
                System.out.println("exit is a shell builtin");
                continue;
            }
            if(s.equals("type type")){
                System.out.println("type is a shell builtin");
                continue;
            }else{
                System.out.println(s+": not found");
                continue;
            }   
        }
        else if(s.equals("exit")){
            break;
        }
        else if(s.startsWith("echo")){
            System.out.println(s.substring(5));
        }else{
        System.out.println(s+": command not found");
        }
        }
   }
}