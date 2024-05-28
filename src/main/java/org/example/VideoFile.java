package org.example;

import static org.example.Constants.FORMATS;
import static org.example.Constants.RESOLUTIONS;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VideoFile {

  private String fileName;
  private String movieName;
  private int resolution;
  private String format;

  public VideoFile(String fileName) {
    this.fileName = fileName;
    for (String format : FORMATS) {
      for (int resolution : RESOLUTIONS) {
        if (fileName.endsWith(format) && fileName.contains(String.valueOf(resolution))) {
          this.movieName = fileName.substring(0, fileName.indexOf("-" + resolution));
          this.resolution = resolution;
          this.format = format;
        }
      }
    }
  }
}
