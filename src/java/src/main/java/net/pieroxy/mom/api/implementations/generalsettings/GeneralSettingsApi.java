package net.pieroxy.mom.api.implementations.generalsettings;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.implementations.reputation.ReputationListDto;
import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
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
 * Settings page (the cog icon next to the profile icon). The web server's connection settings
 * (enabled/port/address) are changed through their own endpoints, applied immediately; everything
 * else through {@code UpdateGeneralSettingsApi}.
 */
@Endpoint(method = ApiMethod.GET)
public class GeneralSettingsApi extends AbstractAuthenticatedEndpoint<GeneralSettingsApiInput, GeneralSettingsApiOutput> {
  public GeneralSettingsApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public GeneralSettingsApiOutput processAuthenticated(GeneralSettingsApiInput input) {
    Configuration config = serviceProvider.getSettingsService().getConfiguration();
    WebServerConfiguration webServer = config.getWebServer();

    String webServerCredentialsKey = webServer != null ? webServer.getCredentials() : null;
    Credential webServerCredential = serviceProvider.getSettingsService().getWebServerCredential();

    List<ReputationListDto> reputationLists = (config.getReputationLists() != null ? config.getReputationLists() : List.<ReputationListConfig>of())
        .stream().map(ReputationListDto::new).collect(Collectors.toList());

    GeneralSettingsApiOutput output = new GeneralSettingsApiOutput(
        config.getDataFolder(),
        config.getKeepLogFiles(),
        webServer != null && webServer.isEnabled(),
        webServer != null ? webServer.getHttpPort() : 0,
        webServer != null ? webServer.getAddress() : null,
        webServerCredentialsKey,
        webServerCredential != null ? webServerCredential.getUsername() : null,
        reputationLists);
    output.setWebServerAddressChangeError(serviceProvider.getWebServerService().getAddressChangeError());
    return output;
  }
}

@TypeScriptType
class GeneralSettingsApiInput extends AuthenticatedApiInput {
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
  /** Why the last scheduled address switch failed, if it did — see WebServerService#changeAddress. */
  private String webServerAddressChangeError;

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

  public String getWebServerAddressChangeError() {
    return webServerAddressChangeError;
  }

  public void setWebServerAddressChangeError(String webServerAddressChangeError) {
    this.webServerAddressChangeError = webServerAddressChangeError;
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
