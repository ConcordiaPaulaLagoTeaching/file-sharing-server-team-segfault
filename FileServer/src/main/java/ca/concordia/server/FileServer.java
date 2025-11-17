package ca.concordia.server;

import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// Commands:
//   CREATE <filename>
//   DELETE <filename>
//   WRITE <filename> <content>
//   READ  <filename>
//   LIST
//   QUIT

public class FileServer {

    // our file system (backed by the single .dat file)
    private final FileSystemManager fsManager;
    // TCP port to listen on
    private final int port;
    // readers–writer lock: many readers or a single writer
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public FileServer(int port, String fileSystemName, int totalSize) {
        this.port = port;
        // spin up the fake disk with the given size
        this.fsManager = new FileSystemManager(fileSystemName, totalSize);
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Listening on port " + port + "...");

            // accept loop – each client goes to its own thread
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client: " + clientSocket);

                Thread clientThread = new Thread(() -> handleClient(clientSocket));
                clientThread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

    // Handles exactly one client connection
    private void handleClient(Socket clientSocket) {
        try (
                Socket socket = clientSocket;
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer =
                        new PrintWriter(socket.getOutputStream(), true)
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    writer.println("ERROR: empty command");
                    continue;
                }

                // split into at most 3 parts so WRITE content can have spaces
                String[] parts = line.split(" ", 3);
                String command = parts[0].toUpperCase();

                try {
                    switch (command) {
                        case "CREATE": {
                            if (parts.length < 2) {
                                writer.println("ERROR: missing filename");
                                break;
                            }
                            String filename = parts[1];
                            if (filename.length() > 11) {
                                writer.println("ERROR: filename too large");
                                break;
                            }

                            rwLock.writeLock().lock(); // exclusive
                            try {
                                fsManager.createFile(filename);
                            } finally {
                                rwLock.writeLock().unlock();
                            }

                            writer.println("SUCCESS: File '" + filename + "' created.");
                            break;
                        }

                        case "WRITE": {
                            if (parts.length < 2) {
                                writer.println("ERROR: missing filename");
                                break;
                            }
                            String filename = parts[1];
                            if (filename.length() > 11) {
                                writer.println("ERROR: filename too large");
                                break;
                            }
                            String content = (parts.length >= 3) ? parts[2] : "";
                            byte[] data = content.getBytes(StandardCharsets.UTF_8);

                            rwLock.writeLock().lock();
                            try {
                                fsManager.writeFile(filename, data);
                            } finally {
                                rwLock.writeLock().unlock();
                            }

                            writer.println("SUCCESS: File '" + filename + "' written.");
                            break;
                        }

                        case "READ": {
                            if (parts.length < 2) {
                                writer.println("ERROR: missing filename");
                                break;
                            }
                            String filename = parts[1];
                            if (filename.length() > 11) {
                                writer.println("ERROR: filename too large");
                                break;
                            }

                            rwLock.readLock().lock();
                            byte[] data;
                            try {
                                data = fsManager.readFile(filename);
                            } finally {
                                rwLock.readLock().unlock();
                            }

                            // send file contents as a single line
                            String body = new String(data, StandardCharsets.UTF_8);
                            writer.println(body);
                            break;
                        }

                        case "DELETE": {
                            if (parts.length < 2) {
                                writer.println("ERROR: missing filename");
                                break;
                            }
                            String filename = parts[1];
                            if (filename.length() > 11) {
                                writer.println("ERROR: filename too large");
                                break;
                            }

                            rwLock.writeLock().lock();
                            try {
                                fsManager.deleteFile(filename);
                            } finally {
                                rwLock.writeLock().unlock();
                            }

                            writer.println("SUCCESS: File '" + filename + "' deleted.");
                            break;
                        }

                        case "LIST": {
                            rwLock.readLock().lock();
                            String[] files;
                            try {
                                files = fsManager.listFiles();
                            } finally {
                                rwLock.readLock().unlock();
                            }

                            // join non-empty names in one line
                            StringBuilder sb = new StringBuilder();
                            if (files != null) {
                                for (String name : files) {
                                    if (name != null && !name.isBlank()) {
                                        if (sb.length() > 0) sb.append(" ");
                                        sb.append(name);
                                    }
                                }
                            }
                            writer.println(sb.toString());
                            break;
                        }

                        case "QUIT":
                            writer.println("SUCCESS: Disconnecting.");
                            return; // end this client thread

                        default:
                            writer.println("ERROR: Unknown command.");
                            break;
                    }
                } catch (Exception e) {
                    // Pass back a friendly error but keep the server alive
                    String msg = e.getMessage();
                    if (msg == null || msg.isBlank()) {
                        msg = "ERROR: internal server error";
                    }
                    // strip newlines so the client still gets a single-line response
                    msg = msg.replace("\r", "").replace("\n", "");
                    writer.println(msg);
                }
            }
        } catch (IOException e) {
            System.err.println("Client connection problem: " + e.getMessage());
        }
    }
}
