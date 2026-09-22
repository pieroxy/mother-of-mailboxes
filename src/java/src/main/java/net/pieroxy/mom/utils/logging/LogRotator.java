package net.pieroxy.mom.utils.logging;

import net.jpountz.lz4.LZ4FrameOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Shifts the existing archives (name.N.lz4 -> name.(N+1).lz4), deletes the ones beyond
 * keepLogFiles, then compresses the current log file into name.1.lz4 and deletes it. Has no
 * dependency on java.util.logging, which makes it testable against plain files; LoggingBootstrap
 * takes care of closing/reopening the FileHandler around this call.
 */
public final class LogRotator {
  private LogRotator() {}

  public static void rotate(String logFile, int keepLogFiles) throws IOException {
    if (keepLogFiles <= 0) return;
    File current = new File(logFile);
    if (!current.isFile()) return;

    for (int i = keepLogFiles - 1; i >= 1; i--) {
      File src = archive(logFile, i);
      if (src.isFile()) {
        Files.move(src.toPath(), archive(logFile, i + 1).toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    }
    for (int i = keepLogFiles + 1; archive(logFile, i).isFile(); i++) {
      Files.delete(archive(logFile, i).toPath());
    }

    compress(current, archive(logFile, 1));
    Files.delete(current.toPath());
  }

  private static File archive(String logFile, int index) {
    return new File(logFile + "." + index + ".lz4");
  }

  private static void compress(File source, File destination) throws IOException {
    // Compress into a temp file then rename atomically: an abrupt interruption (JVM killed,
    // daemon thread cut off mid-write) leaves at worst an orphaned .tmp, never a truncated
    // .lz4 archive under its final name.
    File tmp = new File(destination.getParentFile(), destination.getName() + ".tmp");
    try (InputStream in = new BufferedInputStream(new FileInputStream(source));
        OutputStream out =
            new LZ4FrameOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
      in.transferTo(out);
    }
    Files.move(
        tmp.toPath(),
        destination.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE);
  }
}
