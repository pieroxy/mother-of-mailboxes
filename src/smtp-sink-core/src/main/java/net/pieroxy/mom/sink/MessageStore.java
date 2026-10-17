package net.pieroxy.mom.sink;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * One file per received message, named after the UUID its Subject carried ({@code <uuid>.eml}),
 * deleted {@code retentionSeconds} after it arrived. A message past that age is never served,
 * even if {@link #cleanup} hasn't got to it yet.
 */
public class MessageStore {
  private final static Logger LOGGER = Logger.getLogger(MessageStore.class.getName());
  private final static Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private final static String EXTENSION = ".eml";
  private final static String TMP_EXTENSION = ".tmp";

  private final Path dir;
  private final long retentionMs;

  public MessageStore(String dir, int retentionSeconds) throws IOException {
    this.dir = new File(dir).toPath();
    this.retentionMs = retentionSeconds * 1000L;
    Files.createDirectories(this.dir);
  }

  public static boolean isUuid(String s) {
    return s != null && UUID.matcher(s).matches();
  }

  /** Writes to a temporary file first, so a concurrent {@link #read} never sees a half-written message. */
  public void save(String uuid, byte[] content) throws IOException {
    String name = uuid.toLowerCase(Locale.ROOT);
    Path tmp = dir.resolve(name + TMP_EXTENSION);
    Files.write(tmp, content);
    Files.move(tmp, dir.resolve(name + EXTENSION), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  public Optional<byte[]> read(String uuid) throws IOException {
    if (!isUuid(uuid)) return Optional.empty();
    Path file = dir.resolve(uuid.toLowerCase(Locale.ROOT) + EXTENSION);
    try {
      if (isExpired(file)) return Optional.empty();
      return Optional.of(Files.readAllBytes(file));
    } catch (java.nio.file.NoSuchFileException e) {
      return Optional.empty();
    }
  }

  /** Deletes every expired message (and any temporary file left behind by a crash). */
  public void cleanup() {
    File[] files = dir.toFile().listFiles();
    if (files == null) return;
    int deleted = 0;
    for (File f : files) {
      String name = f.getName();
      if (!name.endsWith(EXTENSION) && !name.endsWith(TMP_EXTENSION)) continue;
      try {
        if (isExpired(f.toPath()) && Files.deleteIfExists(f.toPath())) deleted++;
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Could not delete " + f, e);
      }
    }
    if (deleted > 0) LOGGER.info("Deleted " + deleted + " expired message(s)");
  }

  private boolean isExpired(Path file) throws IOException {
    return System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() >= retentionMs;
  }
}
