package net.pieroxy.mom.detection.classifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent state of the classifier corpus scan: date of the last successful scan (so it only
 * scans once a day) and, per IMAP folder, the last processed UID (so only new messages are
 * re-fetched from one scan to the next).
 */
public class ClassifierScanState {
  private String lastScanDate;
  private Map<String, FolderProgress> folders = new HashMap<>();

  public String getLastScanDate() {
    return lastScanDate;
  }

  public void setLastScanDate(String lastScanDate) {
    this.lastScanDate = lastScanDate;
  }

  public Map<String, FolderProgress> getFolders() {
    return folders;
  }

  public FolderProgress getFolderProgress(String folderFullName) {
    return folders.get(folderFullName);
  }

  public void setFolderProgress(String folderFullName, long uidValidity, long lastUid) {
    FolderProgress progress = new FolderProgress();
    progress.setUidValidity(uidValidity);
    progress.setLastUid(lastUid);
    folders.put(folderFullName, progress);
  }

  /** uidValidity==0 means "never scanned" (an IMAP server never returns 0). */
  public static class FolderProgress {
    private long uidValidity;
    private long lastUid;

    public long getUidValidity() {
      return uidValidity;
    }

    public void setUidValidity(long uidValidity) {
      this.uidValidity = uidValidity;
    }

    public long getLastUid() {
      return lastUid;
    }

    public void setLastUid(long lastUid) {
      this.lastUid = lastUid;
    }
  }
}
