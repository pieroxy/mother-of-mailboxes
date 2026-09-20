package net.pieroxy.imf.detection.classifier;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Persiste {@link ClassifierScanState} sur disque, un fichier JSON par compte. */
public class ClassifierScanStateStore {
  private final static Logger LOGGER = Logger.getLogger(ClassifierScanStateStore.class.getName());
  private final static Gson GSON = new Gson();

  private final File stateFile;

  public ClassifierScanStateStore(String dataFolder, String accountKey) {
    this.stateFile = new File(new File(dataFolder, "classifier-corpus"), accountKey + "-scan-state.json");
  }

  public ClassifierScanState load() {
    if (!stateFile.exists()) return new ClassifierScanState();
    try (FileReader r = new FileReader(stateFile)) {
      ClassifierScanState state = GSON.fromJson(r, ClassifierScanState.class);
      return state != null ? state : new ClassifierScanState();
    } catch (IOException | JsonParseException e) {
      LOGGER.log(Level.WARNING, "Could not read classifier scan state file " + stateFile, e);
      return new ClassifierScanState();
    }
  }

  public void save(ClassifierScanState state) {
    stateFile.getParentFile().mkdirs();
    try (FileWriter w = new FileWriter(stateFile)) {
      GSON.toJson(state, w);
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Could not write classifier scan state file " + stateFile, e);
    }
  }
}
