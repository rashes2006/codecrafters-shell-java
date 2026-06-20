import java.io.File;
import java.util.Scanner;
import java.util.List;

public class Main {

    private static class Job {
        int number;
        long pid;
        String command;
        String status;
        Process process;

        Job(int number, long pid, String command, String status, Process process) {
            this.number = number;
            this.pid = pid;
            this.command = command;
            this.status = status;
            this.process = process;
        }
    }

    private static final List<Job> jobsList = new java.util.ArrayList<>();

    public static void main(String[] args) throws Exception {
        List<String> builtins = List.of("exit", "echo", "type", "pwd", "cd", "jobs");
        Scanner sc = new Scanner(System.in);
        String currentDirectory = System.getProperty("user.dir");

        while (true) {
            reapJobs();
            System.out.print("$ ");
            if (!sc.hasNextLine()) {
break;
            }
            String input = sc.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            // Parse command and arguments (handles single quotes, redirection, and pipelines)
            ParsedCommand parsed = parseArguments(input);
            if (parsed.pipeline.isEmpty()) {
                continue;
            }

            java.io.PrintStream originalOut = System.out;
            java.io.PrintStream originalErr = System.err;

            if (parsed.pipeline.size() == 1) {
                SingleCommand firstCmd = parsed.pipeline.get(0);
                if (firstCmd.args.length == 0) {
                    continue;
                }
                String[] parts = firstCmd.args;
                String command = parts[0];
                String redirectFile = firstCmd.redirectFile;
                String redirectErrFile = firstCmd.redirectErrFile;

                java.io.PrintStream fileOut = null;
                java.io.PrintStream fileErr = null;

                if (redirectFile != null) {
                    try {
                        File outputFile = new File(redirectFile);
                        if (!outputFile.isAbsolute()) {
                            outputFile = new File(currentDirectory, redirectFile);
                        }
                        File parent = outputFile.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }
                        fileOut = new java.io.PrintStream(new java.io.FileOutputStream(outputFile, firstCmd.appendOut));
                        System.setOut(fileOut);
                    } catch (Exception e) {
                        System.err.println(e.getMessage());
                    }
                }

                if (redirectErrFile != null) {
                    try {
                        File errFile = new File(redirectErrFile);
                        if (!errFile.isAbsolute()) {
                            errFile = new File(currentDirectory, redirectErrFile);
                        }
                        File parent = errFile.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }
                        fileErr = new java.io.PrintStream(new java.io.FileOutputStream(errFile, firstCmd.appendErr));
                        System.setErr(fileErr);
                    } catch (Exception e) {
                        originalErr.println(e.getMessage());
                    }
                }

                try {
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
                                System.err.println("cd: " + path + ": No such file or directory");
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
                                    System.err.println(arg + ": not found");
                                }
                            }
                        } else {
                            System.err.println("type: missing argument");
                        }
                    } else if (command.equals("jobs")) {
                        List<Job> finishedJobs = new java.util.ArrayList<>();
                        for (int j = 0; j < jobsList.size(); j++) {
                            Job job = jobsList.get(j);
                            if (job.status.equals("Running") && !job.process.isAlive()) {
                                job.status = "Done";
                            }

                            char marker = ' ';
                            if (j == jobsList.size() - 1) {
                                marker = '+';
                            } else if (j == jobsList.size() - 2) {
                                marker = '-';
                            }
                            String statusField = String.format("%-24s", job.status);
                            String cmdToPrint = job.command;
                            if (job.status.equals("Running")) {
                                cmdToPrint += " &";
                            }
                            System.out.println("[" + job.number + "]" + marker + "  " + statusField + cmdToPrint);

                            if (job.status.equals("Done")) {
                                finishedJobs.add(job);
                            }
                        }
                        jobsList.removeAll(finishedJobs);
                    } else {
                        // For external commands, we restore streams of the shell process,
                        // and configure redirection on the child process itself.
                        System.setOut(originalOut);
                        System.setErr(originalErr);
                        if (fileOut != null) {
                            fileOut.close();
                            fileOut = null;
                        }
                        if (fileErr != null) {
                            fileErr.close();
                            fileErr = null;
                        }

                        String execPath = getExecutablePath(command);
                        if (execPath != null) {
                            try {
                                ProcessBuilder pb = new ProcessBuilder(parts);
                                pb.directory(new File(currentDirectory));
                                if (redirectFile != null) {
                                    File outputFile = new File(redirectFile);
                                    if (!outputFile.isAbsolute()) {
                                        outputFile = new File(currentDirectory, redirectFile);
                                    }
                                    File parent = outputFile.getParentFile();
                                    if (parent != null) {
                                        parent.mkdirs();
                                    }
                                    if (firstCmd.appendOut) {
                                        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outputFile));
                                    } else {
                                        pb.redirectOutput(outputFile);
                                    }
                                } else {
                                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                                }
                                if (redirectErrFile != null) {
                                    File errFile = new File(redirectErrFile);
                                    if (!errFile.isAbsolute()) {
                                        errFile = new File(currentDirectory, redirectErrFile);
                                    }
                                    File parent = errFile.getParentFile();
                                    if (parent != null) {
                                        parent.mkdirs();
                                    }
                                    if (firstCmd.appendErr) {
                                        pb.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
                                    } else {
                                        pb.redirectError(errFile);
                                    }
                                } else {
                                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                                }
                                pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                                Process p = pb.start();
                                if (parsed.runInBackground) {
                                    int jobNum;
                                    if (jobsList.isEmpty()) {
                                        jobNum = 1;
                                    } else {
                                        int maxJobNum = 0;
                                        for (Job job : jobsList) {
                                            if (job.number > maxJobNum) {
                                                maxJobNum = job.number;
                                            }
                                        }
                                        jobNum = maxJobNum + 1;
                                    }
                                    System.out.println("[" + jobNum + "] " + p.pid());
                                    jobsList.add(new Job(jobNum, p.pid(), parsed.commandStr, "Running", p));
                                } else {
                                    p.waitFor();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            System.err.println(command + ": command not found");
                        }
                    }
                } finally {
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                    if (fileOut != null) {
                        fileOut.close();
                    }
                    if (fileErr != null) {
                        fileErr.close();
                    }
                }
            } else if (parsed.pipeline.size() > 1) {
                // Pipeline execution (handles 2 or more external commands)
                System.setOut(originalOut);
                System.setErr(originalErr);

                List<ProcessBuilder> builders = new java.util.ArrayList<>();
                boolean allFound = true;
                for (int i = 0; i < parsed.pipeline.size(); i++) {
                    SingleCommand scmd = parsed.pipeline.get(i);
                    if (scmd.args.length == 0) {
                        allFound = false;
                        break;
                    }
                    String cmdName = scmd.args[0];
                    String execPath = getExecutablePath(cmdName);
                    if (execPath == null) {
                        System.err.println(cmdName + ": command not found");
                        allFound = false;
                        break;
                    }

                    // Replace the executable name with its full path
                    String[] newArgs = java.util.Arrays.copyOf(scmd.args, scmd.args.length);
                    newArgs[0] = execPath;

                    ProcessBuilder pb = new ProcessBuilder(newArgs);
                    pb.directory(new File(currentDirectory));

                    // Redirection for this process in the pipeline
                    // For the first process, default input is inherit unless specified otherwise
                    if (i == 0) {
                        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                    }
                    // For the last process, default output is inherit unless specified otherwise
                    if (i == parsed.pipeline.size() - 1) {
                        if (scmd.redirectFile != null) {
                            File outputFile = new File(scmd.redirectFile);
                            if (!outputFile.isAbsolute()) {
                                outputFile = new File(currentDirectory, scmd.redirectFile);
                            }
                            File parent = outputFile.getParentFile();
                            if (parent != null) {
                                parent.mkdirs();
                            }
                            if (scmd.appendOut) {
                                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outputFile));
                            } else {
                                pb.redirectOutput(outputFile);
                            }
                        } else {
                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        }
                    }

                    // Redirection for error streams: each command can redirect its stderr
                    if (scmd.redirectErrFile != null) {
                        File errFile = new File(scmd.redirectErrFile);
                        if (!errFile.isAbsolute()) {
                            errFile = new File(currentDirectory, scmd.redirectErrFile);
                        }
                        File parent = errFile.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }
                        if (scmd.appendErr) {
                            pb.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
                        } else {
                            pb.redirectError(errFile);
                        }
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }

                    builders.add(pb);
                }

                if (allFound) {
                    try {
                        List<Process> processes = ProcessBuilder.startPipeline(builders);
                        Process lastProcess = processes.get(processes.size() - 1);
                        if (parsed.runInBackground) {
                            int jobNum;
                            if (jobsList.isEmpty()) {
                                jobNum = 1;
                            } else {
                                int maxJobNum = 0;
                                for (Job job : jobsList) {
                                    if (job.number > maxJobNum) {
                                        maxJobNum = job.number;
                                    }
                                }
                                jobNum = maxJobNum + 1;
                            }
                            System.out.println("[" + jobNum + "] " + lastProcess.pid());
                            jobsList.add(new Job(jobNum, lastProcess.pid(), parsed.commandStr, "Running", lastProcess));
                        } else {
                            // Wait for all processes in the pipeline to complete
                            for (Process p : processes) {
                                p.waitFor();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        sc.close();
    }

    private static void reapJobs() {
        List<Job> finishedJobs = new java.util.ArrayList<>();
        for (int j = 0; j < jobsList.size(); j++) {
            Job job = jobsList.get(j);
            if (job.status.equals("Running") && !job.process.isAlive()) {
                job.status = "Done";
            }

            if (job.status.equals("Done")) {
                char marker = ' ';
                if (j == jobsList.size() - 1) {
                    marker = '+';
                } else if (j == jobsList.size() - 2) {
                    marker = '-';
                }
                String statusField = String.format("%-24s", job.status);
                System.out.println("[" + job.number + "]" + marker + "  " + statusField + job.command);
                finishedJobs.add(job);
            }
        }
        jobsList.removeAll(finishedJobs);
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

    private static class Token {
        String text;
        boolean quoted;
        Token(String text, boolean quoted) {
            this.text = text;
            this.quoted = quoted;
        }
    }

    private static class SingleCommand {
        String[] args;
        String redirectFile;
        boolean appendOut;
        String redirectErrFile;
        boolean appendErr;
        SingleCommand(String[] args, String redirectFile, boolean appendOut, String redirectErrFile, boolean appendErr) {
            this.args = args;
            this.redirectFile = redirectFile;
            this.appendOut = appendOut;
            this.redirectErrFile = redirectErrFile;
            this.appendErr = appendErr;
        }
    }

    private static class ParsedCommand {
        List<SingleCommand> pipeline;
        boolean runInBackground;
        String commandStr;
        ParsedCommand(List<SingleCommand> pipeline, boolean runInBackground, String commandStr) {
            this.pipeline = pipeline;
            this.runInBackground = runInBackground;
            this.commandStr = commandStr;
        }
    }

    private static ParsedCommand parseArguments(String input) {
        java.util.List<Token> tokens = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean tokenStarted = false;
        boolean currentTokenQuoted = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inSingleQuote) {
                currentTokenQuoted = true;
                if (c == '\'') {
                    inSingleQuote = false;
                } else {
                    current.append(c);
                }
            } else if (inDoubleQuote) {
                currentTokenQuoted = true;
                if (c == '"') {
                    inDoubleQuote = false;
                } else if (c == '\\') {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '"' || next == '$' || next == '`' || next == '\\' || next == '\n') {
                            i++;
                            current.append(input.charAt(i));
                            tokenStarted = true;
                            continue;
                        }
                    }
                    current.append(c);
                    tokenStarted = true;
                } else {
                    current.append(c);
                    tokenStarted = true;
                }
            } else {
                if (c == '\\') {
                    currentTokenQuoted = true;
                    if (i + 1 < input.length()) {
                        i++;
                        current.append(input.charAt(i));
                        tokenStarted = true;
                    } else {
                        current.append(c);
                        tokenStarted = true;
                    }
                } else if (c == '\'') {
                    inSingleQuote = true;
                    currentTokenQuoted = true;
                    tokenStarted = true;
                } else if (c == '"') {
                    inDoubleQuote = true;
                    currentTokenQuoted = true;
                    tokenStarted = true;
                } else if (c == '&') {
                    if (tokenStarted || current.length() > 0) {
                        tokens.add(new Token(current.toString(), currentTokenQuoted));
                        current.setLength(0);
                        tokenStarted = false;
                        currentTokenQuoted = false;
                    }
                    tokens.add(new Token("&", false));
                } else if (c == '|') {
                    if (tokenStarted || current.length() > 0) {
                        tokens.add(new Token(current.toString(), currentTokenQuoted));
                        current.setLength(0);
                        tokenStarted = false;
                        currentTokenQuoted = false;
                    }
                    tokens.add(new Token("|", false));
                } else if (c == '>') {
                    boolean isAppend = false;
                    if (i + 1 < input.length() && input.charAt(i + 1) == '>') {
                        isAppend = true;
                        i++; // consume second '>'
                    }

                    if (current.length() == 1 && current.charAt(0) == '1' && !currentTokenQuoted) {
                        tokens.add(new Token(isAppend ? "1>>" : "1>", false));
                        current.setLength(0);
                        tokenStarted = false;
                        currentTokenQuoted = false;
                    } else if (current.length() == 1 && current.charAt(0) == '2' && !currentTokenQuoted) {
                        tokens.add(new Token(isAppend ? "2>>" : "2>", false));
                        current.setLength(0);
                        tokenStarted = false;
                        currentTokenQuoted = false;
                    } else {
                        if (tokenStarted || current.length() > 0) {
                            tokens.add(new Token(current.toString(), currentTokenQuoted));
                            current.setLength(0);
                            tokenStarted = false;
                            currentTokenQuoted = false;
                        }
                        tokens.add(new Token(isAppend ? ">>" : ">", false));
                    }
                } else if (c == ' ' || c == '\t') {
                    if (tokenStarted || current.length() > 0) {
                        tokens.add(new Token(current.toString(), currentTokenQuoted));
                        current.setLength(0);
                        tokenStarted = false;
                        currentTokenQuoted = false;
                    }
                } else {
                    current.append(c);
                    tokenStarted = true;
                }
            }
        }

        if (tokenStarted || current.length() > 0) {
            tokens.add(new Token(current.toString(), currentTokenQuoted));
        }

        // Build command string before redirection/background operator is processed
        StringBuilder cmdBuilder = new StringBuilder();
        for (Token t : tokens) {
            if (!t.quoted && t.text.equals("&")) {
                continue;
            }
            if (cmdBuilder.length() > 0) {
                cmdBuilder.append(" ");
            }
            if (t.quoted) {
                cmdBuilder.append("'").append(t.text).append("'");
            } else {
                cmdBuilder.append(t.text);
            }
        }
        String commandStr = cmdBuilder.toString().trim();

        // Process background operator
        boolean runInBackground = false;
        if (!tokens.isEmpty()) {
            Token last = tokens.get(tokens.size() - 1);
            if (!last.quoted && last.text.equals("&")) {
                runInBackground = true;
                tokens.remove(tokens.size() - 1);
            }
        }

        // Split tokens by pipe character '|' (unquoted)
        List<List<Token>> subCommandsTokens = new java.util.ArrayList<>();
        List<Token> currentSub = new java.util.ArrayList<>();
        for (Token t : tokens) {
            if (!t.quoted && t.text.equals("|")) {
                subCommandsTokens.add(currentSub);
                currentSub = new java.util.ArrayList<>();
            } else {
                currentSub.add(t);
            }
        }
        subCommandsTokens.add(currentSub);

        List<SingleCommand> pipeline = new java.util.ArrayList<>();
        for (List<Token> subTokens : subCommandsTokens) {
            String redirectFile = null;
            boolean appendOut = false;
            String redirectErrFile = null;
            boolean appendErr = false;

            for (int i = 0; i < subTokens.size(); i++) {
                Token t = subTokens.get(i);
                if (!t.quoted) {
                    if (t.text.equals(">") || t.text.equals("1>")) {
                        if (i + 1 < subTokens.size()) {
                            redirectFile = subTokens.get(i + 1).text;
                            appendOut = false;
                            subTokens.remove(i + 1);
                            subTokens.remove(i);
                            i--;
                        }
                    } else if (t.text.equals(">>") || t.text.equals("1>>")) {
                        if (i + 1 < subTokens.size()) {
                            redirectFile = subTokens.get(i + 1).text;
                            appendOut = true;
                            subTokens.remove(i + 1);
                            subTokens.remove(i);
                            i--;
                        }
                    } else if (t.text.equals("2>")) {
                        if (i + 1 < subTokens.size()) {
                            redirectErrFile = subTokens.get(i + 1).text;
                            appendErr = false;
                            subTokens.remove(i + 1);
                            subTokens.remove(i);
                            i--;
                        }
                    } else if (t.text.equals("2>>")) {
                        if (i + 1 < subTokens.size()) {
                            redirectErrFile = subTokens.get(i + 1).text;
                            appendErr = true;
                            subTokens.remove(i + 1);
                            subTokens.remove(i);
                            i--;
                        }
                    }
                }
            }

            String[] args = new String[subTokens.size()];
            for (int i = 0; i < subTokens.size(); i++) {
                args[i] = subTokens.get(i).text;
            }
            pipeline.add(new SingleCommand(args, redirectFile, appendOut, redirectErrFile, appendErr));
        }

        return new ParsedCommand(pipeline, runInBackground, commandStr);
    }
}
