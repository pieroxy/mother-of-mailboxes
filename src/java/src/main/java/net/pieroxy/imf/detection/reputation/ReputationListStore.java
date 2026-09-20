package net.pieroxy.imf.detection.reputation;

import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists the raw (text) content of each list, lz4-compressed, one file per id — same pattern
 * as {@code ClassifierCorpusStore}/{@code LogRotator}: atomic write (temp file then rename) so a
 * cache is never left truncated if the process is interrupted mid-save. Serves as the fallback
 * when a refresh fails: the last version that worked stays usable indefinitely rather than
 * losing the signal.
 */
final class ReputationListStore {
  private static final Logger LOGGER = Logger.getLogger(ReputationListStore.class.getName());

  private final File folder;

  ReputationListStore(String dataFolder) {
    this.folder = new File(dataFolder, "reputation");
  }

  /** @return the cached content, or null if no cache exists or it couldn't be read. */
  String load(String id) {
    File file = fileFor(id);
    if (!file.isFile()) return null;
    try (Reader r = new InputStreamReader(new LZ4FrameInputStream(new FileInputStream(file)), StandardCharsets.UTF_8)) {
      StringBuilder sb = new StringBuilder();
      char[] buf = new char[8192];
      int n;
      while ((n = r.read(buf)) != -1) {
        sb.append(buf, 0, n);
      }
      return sb.toString();
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Reputation list [" + id + "]: could not read cached copy " + file, e);
      return null;
    }
  }

  /**
   * Last write date of the cache for this id (epoch millis), or 0 if no cache exists — used to
   * decide whether a network refresh is due (see {@code ReputationRegistry.start()}), rather
   * than just relying on how long the current process has been running: without this, a service
   * stuck in a restart loop (crash loop) would re-download on every restart, potentially at a
   * rate far higher than refreshHours, until it gets banned by the remote source.
   */
  long lastModified(String id) {
    return fileFor(id).lastModified();
  }

  void save(String id, String content) throws IOException {
    folder.mkdirs();
    File file = fileFor(id);
    File tmp = new File(folder, file.getName() + ".tmp");
    try (Writer w = new OutputStreamWriter(new LZ4FrameOutputStream(new FileOutputStream(tmp)), StandardCharsets.UTF_8)) {
      w.write(content);
    }
    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  private File fileFor(String id) {
    return new File(folder, id + ".txt.lz4");
  }
}
