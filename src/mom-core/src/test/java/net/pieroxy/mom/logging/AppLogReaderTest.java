package net.pieroxy.mom.logging;

import net.jpountz.lz4.LZ4FrameOutputStream;
import net.pieroxy.mom.utils.logging.AppLogReader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AppLogReaderTest {
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static String line(int second, String level, String message) {
    return String.format("2026-10-16 10:00:%02d 1234 %7s %30s %s%n", second, level, "net.pieroxy.Foo/bar", message);
  }

  private File logFile(String content) throws IOException {
    File file = new File(tmp.getRoot(), "log.txt");
    Files.writeString(file.toPath(), content);
    return file;
  }

  private static AppLogReader.LogFile current(File file) {
    return new AppLogReader.LogFile(AppLogReader.CURRENT, file, false);
  }

  private static List<String> messages(AppLogReader.Page page) {
    return page.entries().stream().map(AppLogReader.Entry::message).toList();
  }

  @Test
  public void parsesHeadersAndFoldsContinuationLinesIntoDetails() throws IOException {
    File file = logFile(line(1, "INFO", "started")
        + line(2, "SEVERE", "boom")
        + "java.lang.RuntimeException: boom\n\tat Foo.bar(Foo.java:1)\n");

    AppLogReader.Page page = AppLogReader.read(current(file), AppLogReader.Filter.NONE, null, 10);

    assertEquals(List.of("boom", "started"), messages(page));
    AppLogReader.Entry severe = page.entries().get(0);
    assertEquals("2026-10-16 10:00:02", severe.timestamp());
    assertEquals("1234", severe.runId());
    assertEquals("SEVERE", severe.level());
    assertEquals("net.pieroxy.Foo/bar", severe.source());
    assertEquals("java.lang.RuntimeException: boom\n\tat Foo.bar(Foo.java:1)", severe.details());
    assertNull(page.entries().get(1).details());
  }

  @Test
  public void linesBeforeTheFirstHeaderFormAnEntryWithoutLevel() throws IOException {
    File file = logFile("raw output\nmore raw\n" + line(1, "INFO", "started"));

    List<AppLogReader.Entry> entries = AppLogReader.read(current(file), AppLogReader.Filter.NONE, null, 10).entries();

    assertEquals(2, entries.size());
    assertNull(entries.get(1).level());
    assertEquals("raw output", entries.get(1).message());
    assertEquals("more raw", entries.get(1).details());
  }

  @Test
  public void pagesFollowEachOtherWithoutGapsOrDuplicates() throws IOException {
    StringBuilder content = new StringBuilder();
    for (int i = 0; i < 7; i++) content.append(line(i, "INFO", "m" + i));
    File file = logFile(content.toString());

    List<String> all = new ArrayList<>();
    Long cursor = null;
    do {
      AppLogReader.Page page = AppLogReader.read(current(file), AppLogReader.Filter.NONE, cursor, 3);
      all.addAll(messages(page));
      cursor = page.nextCursor();
    } while (cursor != null);

    assertEquals(List.of("m6", "m5", "m4", "m3", "m2", "m1", "m0"), all);
  }

  @Test
  public void filtersByMinimumLevelAndQuery() throws IOException {
    File file = logFile(line(1, "FINE", "detail")
        + line(2, "INFO", "info")
        + line(3, "WARNING", "careful")
        + line(4, "SEVERE", "boom")
        + "Caused by: java.net.BindException: Address already in use\n");

    assertEquals(List.of("boom", "careful"),
        messages(AppLogReader.read(current(file), new AppLogReader.Filter(Level.WARNING, null), null, 10)));
    assertEquals("the query also searches details",
        List.of("boom"), messages(AppLogReader.read(current(file), new AppLogReader.Filter(null, "bindexception"), null, 10)));
  }

  @Test
  public void readsACompressedArchive() throws IOException {
    File archive = new File(tmp.getRoot(), "log.txt.1.lz4");
    try (OutputStream out = new LZ4FrameOutputStream(new FileOutputStream(archive))) {
      out.write((line(1, "INFO", "yesterday")).getBytes(StandardCharsets.UTF_8));
    }

    AppLogReader.Page page = AppLogReader.read(new AppLogReader.LogFile("1", archive, true), AppLogReader.Filter.NONE, null, 10);

    assertEquals(List.of("yesterday"), messages(page));
  }

  @Test
  public void listsTheCurrentFileThenArchivesMostRecentFirst() throws IOException {
    File current = logFile(line(1, "INFO", "now"));
    for (String name : List.of("log.txt.10.lz4", "log.txt.2.lz4", "log.txt.1.lz4", "log.txt.1.lz4.tmp", "other.txt")) {
      Files.writeString(new File(tmp.getRoot(), name).toPath(), "");
    }

    List<String> ids = AppLogReader.listFiles(current.getPath()).stream().map(AppLogReader.LogFile::id).toList();

    assertEquals(List.of(AppLogReader.CURRENT, "1", "2", "10"), ids);
    assertTrue(AppLogReader.listFiles(null).isEmpty());
  }
}
