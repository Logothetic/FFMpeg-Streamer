package org.example;

import static org.example.Constants.FINISHED;
import static org.example.Constants.GET_FILES;

import fr.bmartel.speedtest.SpeedTestReport;
import fr.bmartel.speedtest.SpeedTestSocket;
import fr.bmartel.speedtest.model.SpeedTestError;
import fr.bmartel.speedtest.inter.ISpeedTestListener;

import java.util.Arrays;
import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class StreamingClient {

  private Socket socket;
  private BufferedReader in;
  private PrintWriter out;
  private static final Logger log = Logger.getLogger(StreamingClient.class.getName());
  private final static String SPEED_TEST_SERVER_URI_DL = "http://ipv4.ikoula.testdebit.info/10M.iso";
  private double connectionSpeed = 5.0;
  private String selectedFormat;

  private List<VideoFile> availableFiles = new ArrayList<>();


  public static void main(String[] args) {
    StreamingClient client = new StreamingClient();
    client.startClient();
  }

  public void startClient() {
    testConnectionSpeed();
  }

  private void testConnectionSpeed() {
    SpeedTestSocket speedTestSocket = new SpeedTestSocket();
    speedTestSocket.addSpeedTestListener(new ISpeedTestListener() {
      @Override
      public void onCompletion(SpeedTestReport report) {
        connectionSpeed = report.getTransferRateBit().doubleValue() / 1000000; // Mbps
        log.info("Connection speed: " + connectionSpeed + " Mbps");
//        communicateWithServer();
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
    communicateWithServer();

    speedTestSocket.startDownload(SPEED_TEST_SERVER_URI_DL, 5000);
  }

  private void communicateWithServer() {

    try {
      this.socket = new Socket("127.0.0.1", 8080);
      this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      this.out = new PrintWriter(socket.getOutputStream(), true);

      out.println(GET_FILES);
      Arrays.stream(in.readLine().split(","))
          .forEach(fileName -> availableFiles.add(new VideoFile(fileName)));

      createAndShowGUI();

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

    JComboBox<String> filesComboBox = new JComboBox<>(
        availableFiles.stream().map(VideoFile::getMovieName).toList().toArray(new String[0]));
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

    filesComboBox.addActionListener(e -> {
      String selectedFile = (String) filesComboBox.getSelectedItem();
      updateResolutionsComboBox(pixelSizeComboBox, selectedFile);
    });

    panel.add(pixelSizeComboBox);

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

    for (VideoFile file : availableFiles) {
      if (file.getMovieName().equals(selectedFile)) {
        comboBox.addItem(String.join(",",
            getSuitableResolutions(connectionSpeed, file.getResolution())));
      }
    }

  }

  private List<String> getSuitableResolutions(double connectionSpeed, int fileResolution) {
    List<String> resolutions = new ArrayList<>();
    if (connectionSpeed >= 5.0 && fileResolution == 1080) {
      resolutions.add("1080p");
    }
    if (connectionSpeed >= 2.5 && fileResolution >= 720) {
      resolutions.add("720p");
    }
    if (connectionSpeed >= 1.0 && fileResolution >= 480) {
      resolutions.add("480p");
    }
    if (connectionSpeed >= 0.5 && fileResolution >= 360) {
      resolutions.add("360p");
    }
    resolutions.add("240p");
    return resolutions;
  }

  private void startStreaming(String movieName, String protocol, String format, String pixelSize) {
    out.println(movieName + "-" + pixelSize + "." + format + "," + protocol);
    receiveStream(protocol);
  }

  private void receiveStream(String protocol) {
    ArrayList<String> ffmpegCommand = new ArrayList<>();
    ffmpegCommand.add("ffplay");

    if ("TCP".equalsIgnoreCase(protocol)) {
      ffmpegCommand.add("tcp://127.0.0.1:8081");
    } else if ("UDP".equalsIgnoreCase(protocol)) {
      ffmpegCommand.add("udp://127.0.0.1:8081");
    } else {
      ffmpegCommand.add("-protocol_whitelist");
      ffmpegCommand.add("file,rtp,udp");
      ffmpegCommand.add("-i");
      ffmpegCommand.add("video.sdp");
    }

    try {
      ProcessBuilder processBuilder = new ProcessBuilder(ffmpegCommand);
      Process process = processBuilder.start();
      process.waitFor();
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }
}
