package net.pieroxy.imf.logging;

import net.jpountz.lz4.LZ4FrameInputStream;
import net.pieroxy.imf.utils.logging.LogRotator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogRotatorTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void compressesCurrentLogFileAndRemovesTheOriginal() throws IOException {
    File logFile = tmp.newFile("app.log");
    Files.writeString(logFile.toPath(), "hello world");

    LogRotator.rotate(logFile.getAbsolutePath(), 3);

    assertFalse(logFile.isFile());
    assertEquals("hello world", decompress(new File(logFile.getAbsolutePath() + ".1.lz4")));
  }

  @Test
  public void shiftsExistingArchivesUpBeforeCompressing() throws IOException {
    File logFile = tmp.newFile("app.log");
    Files.writeString(logFile.toPath(), "current");
    Files.writeString(new File(logFile.getAbsolutePath() + ".1.lz4").toPath(), "archive-1");
    Files.writeString(new File(logFile.getAbsolutePath() + ".2.lz4").toPath(), "archive-2");

    LogRotator.rotate(logFile.getAbsolutePath(), 3);

    assertEquals("current", decompress(new File(logFile.getAbsolutePath() + ".1.lz4")));
    assertEquals("archive-1", Files.readString(new File(logFile.getAbsolutePath() + ".2.lz4").toPath()));
    assertEquals("archive-2", Files.readString(new File(logFile.getAbsolutePath() + ".3.lz4").toPath()));
  }

  @Test
  public void dropsArchivesBeyondKeepLogFiles() throws IOException {
    File logFile = tmp.newFile("app.log");
    Files.writeString(logFile.toPath(), "current");
    Files.writeString(new File(logFile.getAbsolutePath() + ".2.lz4").toPath(), "archive-2");

    LogRotator.rotate(logFile.getAbsolutePath(), 2);

    assertFalse(new File(logFile.getAbsolutePath() + ".3.lz4").isFile());
  }

  @Test
  public void doesNothingWhenKeepLogFilesIsZeroOrNegative() throws IOException {
    File logFile = tmp.newFile("app.log");
    Files.writeString(logFile.toPath(), "current");

    LogRotator.rotate(logFile.getAbsolutePath(), 0);

    assertTrue(logFile.isFile());
    assertFalse(new File(logFile.getAbsolutePath() + ".1.lz4").isFile());
  }

  @Test
  public void doesNothingWhenLogFileDoesNotExist() throws IOException {
    File logFile = new File(tmp.getRoot(), "missing.log");

    LogRotator.rotate(logFile.getAbsolutePath(), 3);

    assertFalse(new File(logFile.getAbsolutePath() + ".1.lz4").isFile());
  }

  private static String decompress(File lz4File) throws IOException {
    try (LZ4FrameInputStream in = new LZ4FrameInputStream(Files.newInputStream(lz4File.toPath()))) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
