package org.streamer.client;


import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public class ThreadService implements Runnable {

  private static final Logger log = Logger.getLogger(ThreadService.class.getName());
  private final List<String> ffmpegCommand;

  public ThreadService(List<String> ffmpegCommand) {
    this.ffmpegCommand = ffmpegCommand;
  }

  @Override
  public void run() {
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(ffmpegCommand);
      Process process = processBuilder.start();
      process.waitFor();
      process.destroy();
      log.info("Streaming process completed.");
    } catch (IOException | InterruptedException e) {
      log.severe("Error receiving stream: " + e.getMessage());
    }
  }
}
