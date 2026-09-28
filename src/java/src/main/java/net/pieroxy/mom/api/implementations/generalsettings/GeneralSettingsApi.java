package net.pieroxy.mom.api.implementations.generalsettings;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.implementations.reputation.ReputationListDto;
import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.general.Configuration;
import net.pieroxy.mom.config.general.ReputationListConfig;
import net.pieroxy.mom.config.general.WebServerConfiguration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The parts of config.json that aren't any one account's own settings — for the webapp's General
 * Settings page (the cog icon next to the profile icon). Every field here is editable via
 * {@code UpdateGeneralSettingsApi}, but data folder, log retention and the web server's own
 * connection settings (enabled/port/address) only take effect once the whole process is restarted
 * by hand — nothing in this webapp can trigger that itself, so the page says so next to those
 * fields. Only the web server's own login and the reputation lists apply immediately.
 */
@Endpoint(method = ApiMethod.GET)
public class GeneralSettingsApi extends AbstractApiEndpoint<GeneralSettingsApiInput, GeneralSettingsApiOutput> {
  private final IServiceProvider serviceProvider;

  public GeneralSettingsApi(IServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public GeneralSettingsApiOutput process(GeneralSettingsApiInput input) {
    Configuration config = serviceProvider.getSettingsService().getConfiguration();
    WebServerConfiguration webServer = config.getWebServer();

    String webServerCredentialsKey = webServer != null ? webServer.getCredentials() : null;
    Credential webServerCredential = serviceProvider.getSettingsService().getWebServerCredential();

    List<ReputationListDto> reputationLists = (config.getReputationLists() != null ? config.getReputationLists() : List.<ReputationListConfig>of())
        .stream().map(ReputationListDto::new).collect(Collectors.toList());

    return new GeneralSettingsApiOutput(
        config.getDataFolder(),
        config.getKeepLogFiles(),
        webServer != null && webServer.isEnabled(),
        webServer != null ? webServer.getHttpPort() : 0,
        webServer != null ? webServer.getAddress() : null,
        webServerCredentialsKey,
        webServerCredential != null ? webServerCredential.getUsername() : null,
        reputationLists);
  }
}

@TypeScriptType
class GeneralSettingsApiInput {
}

@TypeScriptType
class GeneralSettingsApiOutput {
  private String dataFolder;
  private int keepLogFiles;
  private boolean webServerEnabled;
  private int webServerHttpPort;
  private String webServerAddress;
  private String webServerCredentialsKey;
  private String webServerUsername;
  private List<ReputationListDto> reputationLists;

  public GeneralSettingsApiOutput() {
  }

  public GeneralSettingsApiOutput(String dataFolder, int keepLogFiles, boolean webServerEnabled, int webServerHttpPort,
                                   String webServerAddress, String webServerCredentialsKey, String webServerUsername,
                                   List<ReputationListDto> reputationLists) {
    this.dataFolder = dataFolder;
    this.keepLogFiles = keepLogFiles;
    this.webServerEnabled = webServerEnabled;
    this.webServerHttpPort = webServerHttpPort;
    this.webServerAddress = webServerAddress;
    this.webServerCredentialsKey = webServerCredentialsKey;
    this.webServerUsername = webServerUsername;
    this.reputationLists = reputationLists;
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

  public boolean isWebServerEnabled() {
    return webServerEnabled;
  }

  public void setWebServerEnabled(boolean webServerEnabled) {
    this.webServerEnabled = webServerEnabled;
  }

  public int getWebServerHttpPort() {
    return webServerHttpPort;
  }

  public void setWebServerHttpPort(int webServerHttpPort) {
    this.webServerHttpPort = webServerHttpPort;
  }

  public String getWebServerAddress() {
    return webServerAddress;
  }

  public void setWebServerAddress(String webServerAddress) {
    this.webServerAddress = webServerAddress;
  }

  public String getWebServerCredentialsKey() {
    return webServerCredentialsKey;
  }

  public void setWebServerCredentialsKey(String webServerCredentialsKey) {
    this.webServerCredentialsKey = webServerCredentialsKey;
  }

  public String getWebServerUsername() {
    return webServerUsername;
  }

  public void setWebServerUsername(String webServerUsername) {
    this.webServerUsername = webServerUsername;
  }

  public List<ReputationListDto> getReputationLists() {
    return reputationLists;
  }

  public void setReputationLists(List<ReputationListDto> reputationLists) {
    this.reputationLists = reputationLists;
  }
}
