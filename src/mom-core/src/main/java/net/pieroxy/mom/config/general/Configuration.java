package net.pieroxy.mom.config.general;

import java.io.File;
import java.util.List;

public class Configuration {
  private List<MailAccountConfiguration> configurations;
  /** Absolute, or relative to the directory holding config.json — see {@link #resolveDataFolder}. */
  private String dataFolder;
  /** See {@code LogRetention}: unset keeps 30 days, 0 keeps everything, N keeps N days. */
  private Integer keepLogFiles;
  /** Reverse proxies whose X-Forwarded-For header is believed: IP addresses or CIDR ranges — see {@code TrustedProxies}. Absent: none. */
  private List<String> trustedProxies;
  /** IP/domain reputation sources (see {@link ReputationListConfig}) — absent = feature disabled. */
  private List<ReputationListConfig> reputationLists;
  private WebServerConfiguration webServer;
  /** Absent: mail server diagnostics disabled. */
  private MailDiagnosticsConfiguration mailDiagnostics;
  /** Set by the first start (see FirstStart) until the web UI's setup wizard completes. */
  private boolean setupInProgress;

  public List<MailAccountConfiguration> getConfigurations() {
    return configurations;
  }

  public void setConfigurations(List<MailAccountConfiguration> configurations) {
    this.configurations = configurations;
  }

  public boolean isSetupInProgress() {
    return setupInProgress;
  }

  public void setSetupInProgress(boolean setupInProgress) {
    this.setupInProgress = setupInProgress;
  }

  public String getDataFolder() {
    return dataFolder;
  }

  public void setDataFolder(String dataFolder) {
    this.dataFolder = dataFolder;
  }

  /**
   * The data folder as an absolute path: {@link #dataFolder} as is if absolute, otherwise relative
   * to {@code configDir} — not to the working directory, so a setup can be moved as one folder.
   */
  public String resolveDataFolder(File configDir) {
    if (dataFolder == null || dataFolder.isBlank()) {
      throw new IllegalStateException("config.json: \"dataFolder\" must be set.");
    }
    File folder = new File(dataFolder);
    if (!folder.isAbsolute()) folder = new File(configDir, dataFolder);
    return folder.getAbsoluteFile().toPath().normalize().toString();
  }

  public Integer getKeepLogFiles() {
    return keepLogFiles;
  }

  public void setKeepLogFiles(Integer keepLogFiles) {
    this.keepLogFiles = keepLogFiles;
  }

  public List<String> getTrustedProxies() {
    return trustedProxies;
  }

  public void setTrustedProxies(List<String> trustedProxies) {
    this.trustedProxies = trustedProxies;
  }

  public List<ReputationListConfig> getReputationLists() {
    return reputationLists;
  }

  public void setReputationLists(List<ReputationListConfig> reputationLists) {
    this.reputationLists = reputationLists;
  }

  public WebServerConfiguration getWebServer() {
    return webServer;
  }

  public void setWebServer(WebServerConfiguration webServer) {
    this.webServer = webServer;
  }

  public MailDiagnosticsConfiguration getMailDiagnostics() {
    return mailDiagnostics;
  }

  public void setMailDiagnostics(MailDiagnosticsConfiguration mailDiagnostics) {
    this.mailDiagnostics = mailDiagnostics;
  }

  /** The configured SMTP sink URL, or null if there's none (diagnostics disabled). */
  public String getMailDiagnosticsSinkUrl() {
    if (mailDiagnostics == null || mailDiagnostics.getSinkUrl() == null || mailDiagnostics.getSinkUrl().isBlank()) return null;
    return mailDiagnostics.getSinkUrl();
  }
}
