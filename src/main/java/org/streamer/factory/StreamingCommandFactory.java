package org.streamer.factory;

import org.streamer.model.VideoFile;
import org.streamer.util.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class StreamingCommandFactory {

  // Logger for logging events in the StreamingCommandFactory
  private static final Logger LOGGER = Logger.getLogger(StreamingCommandFactory.class.getName());

  /**
   * Generates the command line arguments for ffmpeg based on the given video file and protocol.
   *
   * @param videoFile The video file to be streamed.
   * @param protocol  The streaming protocol (UDP, TCP, RTP).
   * @return A list of command line arguments for ffmpeg.
   */
  public List<String> getCommandLineArguments(VideoFile videoFile, String protocol) {
    List<String> commandLineArgs = new ArrayList<>();
    commandLineArgs.add("ffmpeg");
    String inputFilePath = Constants.VIDEO_DIRECTORY + "/" + videoFile.getFileName();

    // Determine the command line arguments based on the protocol
    switch (protocol.toUpperCase()) {
      case "UDP":
        commandLineArgs.addAll(
            Arrays.asList("-re", "-i", inputFilePath, "-f", "mpegts", "udp://127.0.0.1:8080"));
        LOGGER.info("Generated UDP streaming command for file: " + videoFile.getFileName());
        break;
      case "TCP":
        commandLineArgs.addAll(
            Arrays.asList("-i", inputFilePath, "-f", "mpegts", "tcp://127.0.0.1:8081?listen"));
        LOGGER.info("Generated TCP streaming command for file: " + videoFile.getFileName());
        break;
      case "RTP":
        commandLineArgs.addAll(
            Arrays.asList("-re", "-i", inputFilePath, "-an", "-c:v", "copy", "-f", "rtp",
                "-sdp_file", System.getProperty("user.dir") + "/video.sdp",
                "rtp://127.0.0.1:5004?rtcpport=5008"));
        LOGGER.info("Generated RTP streaming command for file: " + videoFile.getFileName());
        break;
      default:
        String errorMessage = "Unsupported protocol: " + protocol;
        LOGGER.severe(errorMessage);
        throw new IllegalArgumentException(errorMessage);
    }

    LOGGER.fine("Command line arguments: " + String.join(" ", commandLineArgs));
    return commandLineArgs;
  }
}
