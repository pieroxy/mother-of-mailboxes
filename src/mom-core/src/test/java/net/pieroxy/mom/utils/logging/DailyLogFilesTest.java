package net.pieroxy.mom.utils.logging;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DailyLogFilesTest {
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
  private final DailyLogFiles files = new DailyLogFiles("access-", ".log");

  private List<String> names(File dir) {
    String[] names = dir.list();
    Arrays.sort(names);
    return List.of(names);
  }

  private void dayFile(File dir, LocalDate day) throws IOException {
    Files.writeString(files.fileFor(dir, day).toPath(), "line\n");
  }

  @Test
  public void theFirstWriteOfADayCompressesEarlierDaysAndDropsExpiredOnes() throws IOException {
    File dir = tmp.getRoot();
    dayFile(dir, TODAY.minusDays(1));
    dayFile(dir, TODAY.minusDays(2));
    dayFile(dir, TODAY.minusDays(3));
    Files.writeString(new File(dir, "log.txt").toPath(), "unrelated");

    files.append(dir, TODAY, "first of the day", 3);

    assertEquals(List.of("access-2026-10-14.log.lz4", "access-2026-10-15.log.lz4", "access-2026-10-16.log", "log.txt"), names(dir));
    assertEquals(List.of("first of the day"), Files.readAllLines(files.fileFor(dir, TODAY).toPath()));
  }

  @Test
  public void foreverKeepsEveryDay() throws IOException {
    File dir = tmp.getRoot();
    dayFile(dir, TODAY.minusDays(400));

    files.append(dir, TODAY, "line", LogRetention.FOREVER);

    assertEquals(List.of("access-2025-09-11.log.lz4", "access-2026-10-16.log"), names(dir));
  }

  @Test
  public void listsDaysMostRecentFirstPreferringTheCompressedFile() throws IOException {
    File dir = tmp.getRoot();
    dayFile(dir, TODAY);
    dayFile(dir, TODAY.minusDays(2));
    Files.writeString(new File(dir, "access-2026-10-15.log").toPath(), "being compressed");
    Files.writeString(new File(dir, "access-2026-10-15.log.lz4").toPath(), "");
    Files.writeString(new File(dir, "log.txt").toPath(), "unrelated");

    List<DailyLogFiles.DayFile> days = files.list(dir);

    assertEquals(List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2)), days.stream().map(DailyLogFiles.DayFile::day).toList());
    assertEquals(List.of(false, true, false), days.stream().map(DailyLogFiles.DayFile::compressed).toList());
  }

  @Test
  public void laterWritesOfTheSameDayJustAppend() throws IOException {
    File dir = tmp.getRoot();
    files.append(dir, TODAY, "one", 30);
    dayFile(dir, TODAY.minusDays(1));

    files.append(dir, TODAY, "two", 30);

    assertEquals("no rotation once today's file exists", List.of("access-2026-10-15.log", "access-2026-10-16.log"), names(dir));
    assertEquals(List.of("one", "two"), Files.readAllLines(files.fileFor(dir, TODAY).toPath()));
  }
}
