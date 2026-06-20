import java.util.Scanner;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        List<String> builtins = List.of("exit", "echo", "type");
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            if (!sc.hasNextLine()) {
                break;
            }
            String input = sc.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            // Split the input into command and argument
            String[] parts = input.split(" ", 2);
            String command = parts[0];
            String arg = parts.length > 1 ? parts[1] : "";

            if (command.equals("exit")) {
                break;
            } else if (command.equals("echo")) {
                System.out.println(arg);
            } else if (command.equals("type")) {
                if (builtins.contains(arg)) {
                    System.out.println(arg + " is a shell builtin");
                } else {
                    System.out.println(arg + ": not found");
                }
            } else {
                System.out.println(input + ": command not found");
            }
        }
        sc.close();
    }
}
