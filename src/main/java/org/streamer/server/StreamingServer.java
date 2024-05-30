package org.streamer.server;

import org.streamer.model.VideoFile;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class StreamingServer {

  private static final Logger LOGGER = Logger.getLogger(StreamingServer.class.getName());
  private final List<VideoFile> availableFiles = new ArrayList<>();
  private final VideoFileScanner fileScanner = new VideoFileScanner();

  public static void main(String[] args) {
    StreamingServer server = new StreamingServer();
    server.startServer();
  }

  public void startServer() {
    LOGGER.info("Starting server...");
    availableFiles.addAll(fileScanner.scanFiles());
    waitForClients();
  }

  private void waitForClients() {
    try (ServerSocket serverSocket = new ServerSocket(8080)) {
      LOGGER.info("Server is listening on port 8080");
      while (true) {
        Socket clientSocket = serverSocket.accept();
        LOGGER.info("New client connected");
        // Handle each client in a new thread
        new ClientHandler(clientSocket, availableFiles).start();
      }
    } catch (IOException e) {
      LOGGER.severe("Error with server socket: " + e.getMessage());
    }
  }
}
