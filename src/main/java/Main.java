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
                System.out.println(a.substring(6));
            }
            else {
            System.out.println(a + ": command not found");}

        }

    }
}
