package net.pieroxy.mom.config.general;

import java.util.List;

public class Configuration {
  private List<MailAccountConfiguration> configurations;
  private String dataFolder;
  private int keepLogFiles;
  /** IP/domain reputation sources (see {@link ReputationListConfig}) — absent = feature disabled. */
  private List<ReputationListConfig> reputationLists;
  private WebServerConfiguration webServer;

  public List<MailAccountConfiguration> getConfigurations() {
    return configurations;
  }

  public void setConfigurations(List<MailAccountConfiguration> configurations) {
    this.configurations = configurations;
  }

  public String getDataFolder() {
    return dataFolder;
  }

  public void setDataFolder(String dataFolder) {
    this.dataFolder = dataFolder;
  }

  public int getKeepLogFiles() {
    return keepLogFiles;
  }

  public void setKeepLogFiles(int keepLogFiles) {
    this.keepLogFiles = keepLogFiles;
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
}
