import java.io.File;
import java.util.Scanner;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        List<String> builtins = List.of("exit", "echo", "type", "pwd", "cd");
        Scanner sc = new Scanner(System.in);
        String currentDirectory = System.getProperty("user.dir");

        while (true) {
            System.out.print("$ ");
            if (!sc.hasNextLine()) {
break;
            }
            String input = sc.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            // Parse command and arguments (handles single quotes)
            String[] parts = parseArguments(input);
            String command = parts[0];

            if (command.equals("exit")) {
                break;
            } else if (command.equals("echo")) {
                if (parts.length > 1) {
                    System.out.println(String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length)));
                } else {
                    System.out.println();
                }
            } else if (command.equals("pwd")) {
                System.out.println(currentDirectory);
            } else if (command.equals("cd")) {
                if (parts.length > 1) {
                    String path = parts[1];
                    File target;
                    if (path.equals("~")) {
                        String home = System.getenv("HOME");
                        target = new File(home != null ? home : System.getProperty("user.home"));
                    } else if (path.startsWith("/")) {
                        target = new File(path);
                    } else {
                        target = new File(currentDirectory, path);
                    }
                    if (target.exists() && target.isDirectory()) {
                        currentDirectory = target.getCanonicalPath();
                    } else {
                        System.out.println("cd: " + path + ": No such file or directory");
                    }
                }
            } else if (command.equals("type")) {
                if (parts.length > 1) {
                    String arg = parts[1];
                    if (builtins.contains(arg)) {
                        System.out.println(arg + " is a shell builtin");
                    } else {
                        String execPath = getExecutablePath(arg);
                        if (execPath != null) {
                            System.out.println(arg + " is " + execPath);
                        } else {
                            System.out.println(arg + ": not found");
                        }
                    }
                } else {
                    System.out.println("type: missing argument");
                }
            } else {
                String execPath = getExecutablePath(command);
                if (execPath != null) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(parts);
                        pb.directory(new File(currentDirectory));
                        pb.inheritIO();
                        Process p = pb.start();
                        p.waitFor();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }
        sc.close();
    }

    private static String getExecutablePath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        String[] directories = pathEnv.split(File.pathSeparator);
        for (String directory : directories) {
            File file = new File(directory, command);
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    private static String[] parseArguments(String input) {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean tokenStarted = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inSingleQuote) {
                if (c == '\'') {
                    inSingleQuote = false;
                } else {
                    current.append(c);
                }
            } else if (inDoubleQuote) {
                if (c == '"') {
                    inDoubleQuote = false;
                } else {
                    // preserve everything literally inside double quotes (spaces too)
                    current.append(c);
                }
            } else {
                if (c == '\'') {
                    inSingleQuote = true;
                    tokenStarted = true;
                } else if (c == '"') {
                    inDoubleQuote = true;
                    tokenStarted = true;
                } else if (c == ' ' || c == '\t') {
                    if (tokenStarted || current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                        tokenStarted = false;
                    }
                    // else skip additional delimiters
                } else {
                    current.append(c);
                    tokenStarted = true;
                }
            }
        }

        if (tokenStarted || current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens.toArray(new String[0]);
    }
}
