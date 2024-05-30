package org.streamer.client;

import fr.bmartel.speedtest.SpeedTestReport;
import fr.bmartel.speedtest.SpeedTestSocket;
import fr.bmartel.speedtest.model.SpeedTestError;
import fr.bmartel.speedtest.inter.ISpeedTestListener;


import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.streamer.model.VideoFile;
import org.streamer.util.Constants;

public class StreamingClient {

  private static final Logger log = Logger.getLogger(StreamingClient.class.getName());
  private static final String SPEED_TEST_SERVER_URI_DL = "ftp://speedtest:speedtest@ftp.otenet.gr/test1Mb.db";

  private Socket socket;
  private BufferedReader in;
  private PrintWriter out;
  private double connectionSpeed;
  private List<VideoFile> availableFiles = new ArrayList<>();
  private Thread streamingThread;


  public static void main(String[] args) {
    StreamingClient client = new StreamingClient();
    client.startClient();
  }

  public void startClient() {
    testConnectionSpeed();
    communicateWithServer();
    createAndShowGUI();
  }

  private void testConnectionSpeed() {
    SpeedTestSocket speedTestSocket = new SpeedTestSocket();
    speedTestSocket.addSpeedTestListener(new ISpeedTestListener() {
      @Override
      public void onCompletion(SpeedTestReport report) {
        connectionSpeed = report.getTransferRateBit().doubleValue() / 1000; // Kbps
        log.info("Connection speed: " + String.format("%.2f", connectionSpeed) + " Kbps");
      }

      @Override
      public void onError(SpeedTestError speedTestError, String errorMessage) {
        log.severe("Speed test error: " + errorMessage);
      }

      @Override
      public void onProgress(float percent, SpeedTestReport report) {
        // Optionally handle progress updates
      }
    });
    speedTestSocket.startDownload(SPEED_TEST_SERVER_URI_DL, 5000);
  }

  private void communicateWithServer() {
    try {
      this.socket = new Socket("127.0.0.1", 8080);
      this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      this.out = new PrintWriter(socket.getOutputStream(), true);

      out.println(Constants.GET_FILES);
      String filesResponse = in.readLine();
      if (filesResponse != null) {
        availableFiles.addAll(Arrays.stream(filesResponse.split(","))
            .map(VideoFile::new)
            .collect(Collectors.toList()));
      }

    } catch (IOException e) {
      log.severe("Error communicating with server: " + e.getMessage());
    }
  }

  private void createAndShowGUI() {
    JFrame frame = new JFrame("Available Files");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(400, 300);

    JPanel panel = new JPanel();
    frame.add(panel);

    JLabel label = new JLabel("Select File:");
    panel.add(label);

    Set<String> uniqueMovieNames = availableFiles.stream()
        .map(VideoFile::getMovieName)
        .collect(Collectors.toCollection(TreeSet::new)); // TreeSet ensures uniqueness and sorts alphabetically

    JComboBox<String> filesComboBox = new JComboBox<>(uniqueMovieNames.toArray(new String[0]));
    panel.add(filesComboBox);

    JLabel protocolLabel = new JLabel("Select Protocol:");
    panel.add(protocolLabel);

    JComboBox<String> protocolComboBox = new JComboBox<>(new String[]{"TCP", "UDP", "RTP"});
    panel.add(protocolComboBox);

    JLabel formatLabel = new JLabel("Select Format:");
    panel.add(formatLabel);

    JComboBox<String> formatComboBox = new JComboBox<>(new String[]{"avi", "mp4", "mkv"});
    panel.add(formatComboBox);

    JLabel pixelSizeLabel = new JLabel("Select Pixel Size:");
    panel.add(pixelSizeLabel);

    JComboBox<String> pixelSizeComboBox = new JComboBox<>();
    panel.add(pixelSizeComboBox);

    filesComboBox.addActionListener(e -> {
      String selectedFile = (String) filesComboBox.getSelectedItem();
      updateResolutionsComboBox(pixelSizeComboBox, selectedFile);
    });

    JButton startButton = new JButton("Start Streaming");
    startButton.addActionListener(e -> {
      String selectedFile = (String) filesComboBox.getSelectedItem();
      String selectedProtocol = (String) protocolComboBox.getSelectedItem();
      String selectedFormat = (String) formatComboBox.getSelectedItem();
      String selectedPixelSize = (String) pixelSizeComboBox.getSelectedItem();
      startStreaming(selectedFile, selectedProtocol, selectedFormat, selectedPixelSize);
    });
    panel.add(startButton);

    frame.setVisible(true);

    // Initialize the resolutions for the first selected file
    if (filesComboBox.getItemCount() > 0) {
      filesComboBox.setSelectedIndex(0);
    }
  }

  private void updateResolutionsComboBox(JComboBox<String> comboBox, String selectedFile) {
    comboBox.removeAllItems();
    availableFiles.stream()
        .filter(file -> file.getMovieName().equals(selectedFile))
        .flatMap(file -> getSuitableResolutions(connectionSpeed, file.getResolution()).stream())
        .distinct()
        .forEach(comboBox::addItem);
  }

  private List<String> getSuitableResolutions(double connectionSpeed, int fileResolution) {
    List<String> resolutions = new ArrayList<>();
    if (connectionSpeed >= 5000 && fileResolution >= 1080) resolutions.add("1080p");
    if (connectionSpeed >= 2500 && fileResolution >= 720) resolutions.add("720p");
    if (connectionSpeed >= 1000 && fileResolution >= 480) resolutions.add("480p");
    if (connectionSpeed >= 500 && fileResolution >= 360) resolutions.add("360p");
    resolutions.add("240p");
    return resolutions;
  }

  private void startStreaming(String movieName, String protocol, String format, String pixelSize) {
    out.println(movieName + "-" + pixelSize + "." + format + "," + protocol);
    receiveStream(protocol);
  }

  private void receiveStream(String protocol) {
    List<String> ffmpegCommand = new ArrayList<>();
    ffmpegCommand.add("ffplay");

    switch (protocol.toUpperCase()) {
      case "TCP":
        ffmpegCommand.add("tcp://127.0.0.1:8081");
        break;
      case "UDP":
        ffmpegCommand.add("udp://127.0.0.1:8080");
        break;
      case "RTP":
        ffmpegCommand.add("-protocol_whitelist");
        ffmpegCommand.add("file,rtp,udp");
        ffmpegCommand.add("-i");
        ffmpegCommand.add("video.sdp");
        break;
      default:
        throw new IllegalArgumentException("Unsupported protocol: " + protocol);
    }
    ThreadService threadService = new ThreadService(ffmpegCommand);
    streamingThread = new Thread(threadService);
    streamingThread.start();
  }
}
