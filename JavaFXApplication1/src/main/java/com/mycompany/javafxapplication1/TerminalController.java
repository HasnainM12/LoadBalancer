package com.mycompany.javafxapplication1;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import java.io.*;
import java.nio.file.*;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.List;

public class TerminalController {
    @FXML
    private TextArea terminalArea;

    private String currentDirectory = System.getProperty("user.home");
    private Stack<String> commandHistory = new Stack<>();
    private int historyIndex = -1;
    private String currentPrompt = "";
    private Session session;

    @FXML
    public void initialize() {
        session = Session.getInstance();
        terminalArea.setEditable(true);
        terminalArea.setWrapText(true);
        showPrompt();
        terminalArea.setOnKeyPressed(this::handleKeyPress);
    }

    private void handleCommand(String command) {
        String[] parts = command.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return;

        switch (parts[0]) {
            case "ls":
                listDirectory(parts.length > 1 ? parts[1] : ".");
                break;
            case "mkdir":
                if (parts.length > 1) createDirectory(parts[1]);
                else appendToTerminal("mkdir: missing operand");
                break;
            case "whoami":
                showCurrentUser();
                break;
            case "ps":
                listProcesses();
                break;
            case "tree":
                if (parts.length > 1) showDirectoryTree(parts[1], "", true);
                else showDirectoryTree(".", "", true);
                break;
            case "cp":
                if (parts.length > 2) copyFile(parts[1], parts[2]);
                else appendToTerminal("cp: missing operand");
                break;
            case "mv":
                if (parts.length > 2) moveFile(parts[1], parts[2]);
                else appendToTerminal("mv: missing operand");
                break;
            case "nano":
                if (parts.length > 1) openEditor(parts[1]);
                else appendToTerminal("nano: missing filename");
                break;
            case "cd":
                changeDirectory(parts.length > 1 ? parts[1] : System.getProperty("user.home"));
                break;
            case "clear":
                terminalArea.clear();
                break;
            default:
                executeExternalCommand(command);
        }
    }

    private void listDirectory(String path) {
        try {
            Path dir = resolvePath(path);
            Files.list(dir)
                .map(p -> {
                    String name = p.getFileName().toString();
                    return Files.isDirectory(p) ? name + "/" : name;
                })
                .sorted()
                .forEach(name -> appendToTerminal(name));
        } catch (IOException e) {
            appendToTerminal("ls: cannot access '" + path + "': " + e.getMessage());
        }
    }

    private void createDirectory(String dirName) {
        try {
            Path newDir = resolvePath(dirName);
            Files.createDirectories(newDir);
        } catch (IOException e) {
            appendToTerminal("mkdir: cannot create directory '" + dirName + "': " + e.getMessage());
        }
    }

    private void showCurrentUser() {
        appendToTerminal(session.getUsername());
    }

    private void listProcesses() {
        ProcessHandle.allProcesses()
            .limit(10)
            .forEach(process -> {
                String cmd = process.info().command().orElse("N/A");
                appendToTerminal(String.format("%d %s", process.pid(), cmd));
            });
    }

    private void showDirectoryTree(String path, String indent, boolean isLast) {
        try {
            Path dir = resolvePath(path);
            appendToTerminal(indent + (isLast ? "└── " : "├── ") + dir.getFileName());
            
            if (Files.isDirectory(dir)) {
                List<Path> files = Files.list(dir).sorted().collect(Collectors.toList());
                for (int i = 0; i < files.size(); i++) {
                    showDirectoryTree(
                        files.get(i).toString(),
                        indent + (isLast ? "    " : "│   "),
                        i == files.size() - 1
                    );
                }
            }
        } catch (IOException e) {
            appendToTerminal("tree: error accessing '" + path + "': " + e.getMessage());
        }
    }

    private void copyFile(String source, String dest) {
        try {
            Path sourcePath = resolvePath(source);
            Path destPath = resolvePath(dest);
            Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            appendToTerminal("cp: cannot copy '" + source + "': " + e.getMessage());
        }
    }

    private void moveFile(String source, String dest) {
        try {
            Path sourcePath = resolvePath(source);
            Path destPath = resolvePath(dest);
            Files.move(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            appendToTerminal("mv: cannot move '" + source + "': " + e.getMessage());
        }
    }

    private void openEditor(String filename) {
        try {
            Path filePath = resolvePath(filename);
            String content = Files.exists(filePath) ? 
                Files.readString(filePath) : "";
            
            // Clear terminal and show editor interface
            terminalArea.clear();
            appendToTerminal("Nano Editor - " + filename);
            appendToTerminal("Ctrl+S to save, Ctrl+X to exit\n");
            terminalArea.appendText(content);
            
            // Add special handler for editor mode
            terminalArea.setOnKeyPressed(event -> {
                if (event.isControlDown()) {
                    if (event.getCode() == KeyCode.S) {
                        try {
                            Files.writeString(filePath, terminalArea.getText());
                            appendToTerminal("\nFile saved.");
                        } catch (IOException e) {
                            appendToTerminal("\nError saving file: " + e.getMessage());
                        }
                    } else if (event.getCode() == KeyCode.X) {
                        // Restore normal terminal mode
                        terminalArea.clear();
                        terminalArea.setOnKeyPressed(this::handleKeyPress);
                        showPrompt();
                    }
                }
            });
        } catch (IOException e) {
            appendToTerminal("nano: error opening '" + filename + "': " + e.getMessage());
        }
    }

    private Path resolvePath(String path) {
        return Paths.get(currentDirectory).resolve(path).normalize();
    }

    private void executeExternalCommand(String command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-c", command);
            processBuilder.directory(new File(currentDirectory));
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                appendToTerminal(line);
            }

            process.waitFor();
        } catch (Exception e) {
            appendToTerminal("Error: " + e.getMessage());
        }
    }

    // Rest of the existing methods remain unchanged
    private void handleKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            event.consume();
            String command = getCurrentCommand();
            handleCommand(command);
            if (!command.trim().isEmpty()) {
                commandHistory.push(command);
                historyIndex = -1;
            }
            showPrompt();
        } else if (event.getCode() == KeyCode.UP) {
            event.consume();
            showPreviousCommand();
        } else if (event.getCode() == KeyCode.DOWN) {
            event.consume();
            showNextCommand();
        }
    }

    private void showPrompt() {
        String prompt = session.getUsername() + "@terminal:" + currentDirectory + "$ ";
        appendToTerminal("\n" + prompt);
        currentPrompt = prompt;
    }

    private String getCurrentCommand() {
        String text = terminalArea.getText();
        return text.substring(text.lastIndexOf(currentPrompt) + currentPrompt.length());
    }

    private void showPreviousCommand() {
        if (!commandHistory.isEmpty() && historyIndex < commandHistory.size() - 1) {
            historyIndex++;
            String command = commandHistory.get(commandHistory.size() - 1 - historyIndex);
            setCurrentCommand(command);
        }
    }

    private void showNextCommand() {
        if (historyIndex > 0) {
            historyIndex--;
            String command = commandHistory.get(commandHistory.size() - 1 - historyIndex);
            setCurrentCommand(command);
        } else if (historyIndex == 0) {
            historyIndex = -1;
            setCurrentCommand("");
        }
    }

    private void setCurrentCommand(String command) {
        String text = terminalArea.getText();
        int promptIndex = text.lastIndexOf(currentPrompt);
        terminalArea.setText(text.substring(0, promptIndex + currentPrompt.length()) + command);
        terminalArea.positionCaret(terminalArea.getText().length());
    }

    private void appendToTerminal(String message) {
        terminalArea.appendText(message + "\n");
    }


private void changeDirectory(String newPath) {
    try {
        File newDir;
        if (newPath.startsWith("/")) {
            newDir = new File(newPath);
        } else {
            newDir = new File(currentDirectory, newPath);
        }

        if (newDir.exists() && newDir.isDirectory()) {
            currentDirectory = newDir.getCanonicalPath();
        } else {
            appendToTerminal("cd: no such directory: " + newPath);
        }
    } catch (IOException e) {
        appendToTerminal("Error changing directory: " + e.getMessage());
    }
}

}
