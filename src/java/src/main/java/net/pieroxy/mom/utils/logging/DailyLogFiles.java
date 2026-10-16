package net.pieroxy.mom.utils.logging;

import net.jpountz.lz4.LZ4FrameOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One file per day, {@code <prefix>yyyy-MM-dd<extension>}, appended to line by line. Rotation is
 * lazy: the first write of a day compresses every earlier day still plain into {@code .lz4}, and
 * deletes the files beyond the retention. Callers synchronize their writes.
 */
public final class DailyLogFiles {
  private final static Logger LOGGER = Logger.getLogger(DailyLogFiles.class.getName());

  private final String prefix;
  private final String extension;
  private final Pattern dayFile;

  public DailyLogFiles(String prefix, String extension) {
    this.prefix = prefix;
    this.extension = extension;
    this.dayFile = Pattern.compile(Pattern.quote(prefix) + "(\\d{4}-\\d{2}-\\d{2})" + Pattern.quote(extension));
  }

  public File fileFor(File dir, LocalDate day) {
    return new File(dir, prefix + day + extension);
  }

  /** @param retentionDays days to keep, {@link LogRetention#FOREVER} for all. */
  public void append(File dir, LocalDate today, String line, int retentionDays) throws IOException {
    File file = fileFor(dir, today);
    Files.createDirectories(dir.toPath());
    if (!file.isFile()) rotate(dir, today, retentionDays);
    try (Writer writer = new FileWriter(file, true)) {
      writer.write(line);
      writer.write(System.lineSeparator());
    }
  }

  /**
   * Compresses every day file other than today's that isn't already a {@code .lz4} (several if
   * nothing was written for a few days), then deletes anything dated {@code retentionDays} or more
   * before today.
   */
  void rotate(File dir, LocalDate today, int retentionDays) {
    File[] files = dir.listFiles();
    if (files == null) return;
    LocalDate cutoff = today.minusDays(retentionDays);
    for (File f : files) {
      String name = f.getName();
      boolean compressed = name.endsWith(".lz4");
      Matcher m = dayFile.matcher(compressed ? name.substring(0, name.length() - ".lz4".length()) : name);
      if (!m.matches()) continue;

      LocalDate fileDate = LocalDate.parse(m.group(1));
      if (retentionDays != LogRetention.FOREVER && !fileDate.isAfter(cutoff)) {
        if (!f.delete()) LOGGER.warning("Could not delete expired log file " + f);
      } else if (!compressed && fileDate.isBefore(today)) {
        compress(f);
      }
    }
  }

  /** Compresses source into source.lz4, then deletes source — same temp-file-then-atomic-move approach as {@link LogRotator}. */
  private static void compress(File source) {
    File destination = new File(source.getParentFile(), source.getName() + ".lz4");
    File tmp = new File(destination.getParentFile(), destination.getName() + ".tmp");
    try {
      try (InputStream in = new BufferedInputStream(new FileInputStream(source));
          OutputStream out = new LZ4FrameOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
        in.transferTo(out);
      }
      Files.move(tmp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      Files.delete(source.toPath());
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Could not compress log file " + source, e);
    }
  }
}
