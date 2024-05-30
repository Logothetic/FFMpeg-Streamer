package org.streamer.server;

import org.streamer.factory.StreamingCommandFactory;
import org.streamer.model.VideoFile;
import org.streamer.util.Constants;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ClientHandler extends Thread {
  private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());
  private final Socket clientSocket;
  private final List<VideoFile> availableFiles;

  public ClientHandler(Socket socket, List<VideoFile> availableFiles) {
    this.clientSocket = socket;
    this.availableFiles = availableFiles;
  }

  @Override
  public void run() {
    try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

      // Handle client's request for available files
      String clientRequest = in.readLine();
      if (Constants.GET_FILES.equals(clientRequest)) {
        String fileList = availableFiles.stream()
            .map(VideoFile::getFileName)
            .collect(Collectors.joining(","));
        out.println(fileList);
        LOGGER.info("Sent available files list to client");
      }

      // Handle client's streaming request
      String request = in.readLine();
      if (request != null) {
        LOGGER.info("Received streaming request: " + request);
        streamFileToClient(request);
      }

    } catch (IOException e) {
      LOGGER.severe("Error handling client: " + e.getMessage());
    } finally {
      try {
        clientSocket.close();
      } catch (IOException e) {
        LOGGER.severe("Error closing client socket: " + e.getMessage());
      }
    }
  }

  private void streamFileToClient(String clientRequest) {
    String[] requestParts = clientRequest.split(",");
    String fileName = requestParts[0];
    String protocol = requestParts[1];

    VideoFile videoFile = new VideoFile(fileName);
    StreamingCommandFactory commandFactory = new StreamingCommandFactory();

    List<String> commandLineArgs = commandFactory.getCommandLineArguments(videoFile, protocol);
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(commandLineArgs);
      Process streamerHost = processBuilder.start();
      streamerHost.waitFor();
      streamerHost.destroy();
      LOGGER.info("Streaming process completed for " + fileName);
    } catch (IOException | InterruptedException e) {
      LOGGER.severe("Streaming error: " + e.getMessage());
    }
  }
}
