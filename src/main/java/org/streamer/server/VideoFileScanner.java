package org.streamer.server;

import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import org.streamer.model.VideoFile;
import org.streamer.util.Constants;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

class VideoFileScanner {

  // Logger for logging events in the VideoFileScanner
  private static final Logger LOGGER = Logger.getLogger(VideoFileScanner.class.getName());

  // Method to scan the video directory and return a list of available video files
  public List<VideoFile> scanFiles() {
    List<VideoFile> availableFiles = new ArrayList<>();
    File dir = new File(Constants.VIDEO_DIRECTORY);

    // Stream through files in the directory, filter, and process them
    Arrays.stream(Objects.requireNonNull(dir.listFiles()))
        .filter(File::isFile)
        .map(File::getName)
        .forEach(fileName -> {
          VideoFile videoFile = new VideoFile(fileName);

          // Convert missing formats and resolutions for the video file
          convertMissingFiles(videoFile);

          // Add the video file to the list of available files
          availableFiles.add(videoFile);

          // Log the addition of the video file
          LOGGER.info("Added video file: " + fileName);
        });

    // Log the completion of the file scanning process
    LOGGER.info("Completed scanning of files.");
    return availableFiles;
  }

  // Method to convert missing formats and resolutions for a given video file
  private void convertMissingFiles(VideoFile videoFile) {
    for (String format : Constants.FORMATS) {
      for (int resolution : Constants.RESOLUTIONS) {
        // Check if the file needs conversion based on resolution and format
        if (resolution <= videoFile.getResolution() && !format.equals(videoFile.getFormat())) {
          convertFile(videoFile, resolution, format);
        }
      }
    }
  }

  // Method to convert a video file to a specific resolution and format
  private void convertFile(VideoFile videoFile, int resolution, String format) {
    String inputFilePath = Constants.VIDEO_DIRECTORY + "/" + videoFile.getFileName();
    String outputFilePath =
        Constants.VIDEO_DIRECTORY + "/" + videoFile.getMovieName() + "-" + resolution + "p." + format;

    // Execute the FFmpeg command to perform the conversion
    FFmpeg.atPath()
        .addInput(UrlInput.fromPath(new File(inputFilePath).toPath()))
        .addOutput(UrlOutput.toPath(new File(outputFilePath).toPath()))
        .execute();

    // Log the conversion details
    LOGGER.info("Converted " + inputFilePath + " to " + outputFilePath);
  }
}
