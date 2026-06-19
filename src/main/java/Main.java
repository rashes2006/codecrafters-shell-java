import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        while (true) {
            System.out.print("$ ");
            Scanner sc = new Scanner(System.in);
            String a = sc.nextLine();
            if (a.equals("exit")){
                break;
            }
            else if (a.startsWith("echo")){
                System.out.println(a.substring(5));
            }
            else if (a.startsWith("type")){
                if (a.substring(5).equals("echo")){
                    System.out.println("echo is a shell builtin");
                }
                else if (a.substring(5).equals("echo")){
                    System.out.println("exit is a shell builtin");
                }
            }
            else {
            System.out.println(a + ": command not found");}

        }

    }
}
