package org.example;

import static org.example.Constants.*;

import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.logging.*;
import java.util.stream.Collectors;

public class StreamingServer {

  private static final Logger LOGGER = Logger.getLogger(StreamingServer.class.getName());

  private ArrayList<VideoFile> availableFiles = new ArrayList<>();

  public static void main(String[] args) {
    StreamingServer server = new StreamingServer();
    server.startServer();
  }

  public void startServer() {
    LOGGER.info("Starting server...");
    scanFiles();
    waitForClients();
  }

  private void scanFiles() {
    File dir = new File(VIDEO_DIRECTORY);
    Arrays.stream(Objects.requireNonNull(dir.listFiles()))
        .filter(File::isFile)
        .map(File::getName)
        .forEach(fileName -> {
          VideoFile videoFile = new VideoFile(fileName);
          convertMissingFiles(videoFile);
        });

    Arrays.stream(Objects.requireNonNull(dir.listFiles()))
        .filter(File::isFile)
        .map(File::getName)
        .forEach(fileName -> availableFiles.add(new VideoFile(fileName)));
    LOGGER.info("Completed scanning of files.");
  }

  private void convertMissingFiles(VideoFile videoFile) {
    for (String format : FORMATS) {
      for (int resolution : RESOLUTIONS) {
        if (resolution <= videoFile.getResolution() && !format.equals(videoFile.getFormat())) {
          convertFile(videoFile, resolution, format);
        }
      }
    }
  }

  private void convertFile(VideoFile videoFile, int resolution, String format) {
    String inputFilePath = VIDEO_DIRECTORY + "/" + videoFile.getFileName();
    String outputFilePath =
        VIDEO_DIRECTORY + "/" + videoFile.getMovieName() + "-" + resolution + "p." + format;

    FFmpeg.atPath()
        .addInput(UrlInput.fromPath(new File(inputFilePath).toPath()))
        .addOutput(UrlOutput.toPath(new File(outputFilePath).toPath()))
        .execute();

    LOGGER.info("Converted " + inputFilePath + " to " + outputFilePath);
  }

  private void waitForClients() {
    try (ServerSocket serverSocket = new ServerSocket(8080)) {
      while (true) {
        Socket clientSocket = serverSocket.accept();
        handleClient(clientSocket);
      }
    } catch (IOException e) {
      LOGGER.severe("Error with server socket: " + e.getMessage());
    }
  }

  private void handleClient(Socket clientSocket) {
    try (BufferedReader in = new BufferedReader(
        new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

      String clientRequest = in.readLine();
      if (clientRequest.equals(GET_FILES)) {

        out.println(availableFiles.stream()
            .map(VideoFile::getFileName)
            .collect(Collectors.joining(",")));
      }

      String request = null;
      while (request == null) {
        request = in.readLine();
      }
      streamFileToClient(request);

    } catch (IOException e) {
      LOGGER.severe("Error handling client: " + e.getMessage());
    }
  }

  private void streamFileToClient(String clientRequest) {
    String[] requestParts = clientRequest.split(",");
    String fileName = requestParts[0];
    String protocol = requestParts[1];

    VideoFile videoFile = new VideoFile(fileName);

    ArrayList<String> command_line_args = new ArrayList<>();
    command_line_args.add("ffmpeg");
    switch (protocol) {
      case "UDP" -> {
        command_line_args.add("-re");
        command_line_args.add("-i");
        command_line_args.add(VIDEO_DIRECTORY + "/" + videoFile.getFileName());
        command_line_args.add("-f");
        command_line_args.add("mpegts");
        command_line_args.add(getOutputUrl(protocol));
      }
      case "TCP" -> {
        command_line_args.add("-i");
        command_line_args.add(VIDEO_DIRECTORY + "/" + videoFile.getFileName());
        command_line_args.add("-f");
        command_line_args.add("mpegts");
        command_line_args.add(getOutputUrl(protocol));
      }
      case "RTP" -> {
        command_line_args.add("-re");
        command_line_args.add("-i");
        command_line_args.add(VIDEO_DIRECTORY + "/" + videoFile.getFileName());
        command_line_args.add("-an");
        command_line_args.add("-c:v");
        command_line_args.add("copy");
        command_line_args.add("-f");
        command_line_args.add("rtp");
        command_line_args.add("-sdp_file");
        command_line_args.add(System.getProperty("user.dir") + "/video.sdp");
        command_line_args.add(getOutputUrl(protocol));
      }
    }

    try {
      ProcessBuilder process_builder = new ProcessBuilder(command_line_args);
      Process streamer_host = process_builder.start();
      streamer_host.waitFor();
      LOGGER.info("Server has started streaming");
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

  }

  private String getOutputUrl(String protocol) {
    switch (protocol.toUpperCase()) {
      case "TCP":
        return "tcp://127.0.0.1:8081?listen";
      case "UDP":
        return "udp://127.0.0.1:8081";
      case "RTP":
        return "rtp://127.0.0.1:5004?rtcpport=5008";
      default:
        throw new IllegalArgumentException("Unsupported protocol: " + protocol);
    }
  }

}
