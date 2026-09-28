package net.pieroxy.mom.api.implementations.generalsettings;

import net.pieroxy.mom.services.ServiceProvider;
import net.pieroxy.mom.services.SettingsService;
import net.pieroxy.mom.api.implementations.reputation.ReputationListDto;
import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.ReputationListConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Saves every field the general settings page offers, via {@link SettingsService#updateGeneralSettings}.
 * The web server's own login and the whole reputation lists list apply immediately, with no
 * restart; data folder, log retention and the web server's own connection settings are persisted
 * to config.json but only take effect once the whole process is restarted by hand — see
 * {@code GeneralSettingsApi}'s class javadoc.
 */
@Endpoint(method = ApiMethod.POST)
public class UpdateGeneralSettingsApi extends AbstractApiEndpoint<UpdateGeneralSettingsApiInput, UpdateGeneralSettingsApiOutput> {
  private final ServiceProvider serviceProvider;

  public UpdateGeneralSettingsApi(ServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public UpdateGeneralSettingsApiOutput process(UpdateGeneralSettingsApiInput input) {
    if (input.getDataFolder() == null || input.getDataFolder().isBlank()) {
      throw new IllegalArgumentException("The data folder must not be blank.");
    }
    if (input.getKeepLogFiles() < 0) {
      throw new IllegalArgumentException("Keep log files must be zero (disabled) or a positive number of days.");
    }
    if (input.isWebServerEnabled() && (input.getWebServerHttpPort() < 1 || input.getWebServerHttpPort() > 65535)) {
      throw new IllegalArgumentException("The web server port must be between 1 and 65535.");
    }
    if (input.getWebServerUsername() == null || input.getWebServerUsername().isBlank()) {
      throw new IllegalArgumentException("Web server username must not be blank.");
    }

    List<ReputationListDto> dtos = input.getReputationLists() != null ? input.getReputationLists() : List.of();
    Set<String> seenIds = new HashSet<>();
    for (ReputationListDto dto : dtos) {
      if (dto.getId() == null || dto.getId().isBlank()) {
        throw new IllegalArgumentException("A reputation list needs an id.");
      }
      if (!seenIds.add(dto.getId())) {
        throw new IllegalArgumentException("Reputation list id \"" + dto.getId() + "\" is used more than once.");
      }
      if (dto.getType() == null) {
        throw new IllegalArgumentException("Reputation list \"" + dto.getId() + "\" needs a type.");
      }
      if (dto.getUrl() == null || dto.getUrl().isBlank()) {
        throw new IllegalArgumentException("Reputation list \"" + dto.getId() + "\" needs a URL.");
      }
      if (dto.getRefreshHours() <= 0) {
        throw new IllegalArgumentException("Reputation list \"" + dto.getId() + "\" needs a positive refresh interval.");
      }
      if (dto.getScore() < 0 || dto.getScore() > 1) {
        throw new IllegalArgumentException("Reputation list \"" + dto.getId() + "\" score must be between 0 and 1.");
      }
    }

    List<ReputationListConfig> reputationLists = dtos.stream().map(ReputationListDto::toConfig).collect(Collectors.toList());
    serviceProvider.getSettingsService().updateGeneralSettings(input.getDataFolder(), input.getKeepLogFiles(), input.isWebServerEnabled(),
        input.getWebServerHttpPort(), input.getWebServerAddress(), input.getWebServerUsername(),
        input.getWebServerPassword(), reputationLists);

    return new UpdateGeneralSettingsApiOutput();
  }
}

@TypeScriptType
class UpdateGeneralSettingsApiInput {
  private String dataFolder;
  private int keepLogFiles;
  private boolean webServerEnabled;
  private int webServerHttpPort;
  private String webServerAddress;
  private String webServerUsername;
  /** Blank means "leave the current password unchanged" — same convention as an account's credentials. */
  private String webServerPassword;
  private List<ReputationListDto> reputationLists;

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

  public String getWebServerUsername() {
    return webServerUsername;
  }

  public void setWebServerUsername(String webServerUsername) {
    this.webServerUsername = webServerUsername;
  }

  public String getWebServerPassword() {
    return webServerPassword;
  }

  public void setWebServerPassword(String webServerPassword) {
    this.webServerPassword = webServerPassword;
  }

  public List<ReputationListDto> getReputationLists() {
    return reputationLists;
  }

  public void setReputationLists(List<ReputationListDto> reputationLists) {
    this.reputationLists = reputationLists;
  }
}

@TypeScriptType
class UpdateGeneralSettingsApiOutput {
}
