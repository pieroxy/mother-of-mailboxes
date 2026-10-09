package net.pieroxy.mom.api.implementations.setup;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.services.SettingsService;
import net.pieroxy.mom.utils.logging.LoggingBootstrap;

import java.io.File;
import java.util.logging.Logger;

/** Setup wizard's storage step: data folder and log retention, applied right away — see {@link SettingsService#saveSetupStorage}. */
@Endpoint(method = ApiMethod.POST)
public class SaveSetupStorageApi extends AbstractAuthenticatedEndpoint<SaveSetupStorageApiInput, SaveSetupStorageApiOutput> {
  private final static Logger LOGGER = Logger.getLogger(SaveSetupStorageApi.class.getName());

  public SaveSetupStorageApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public SaveSetupStorageApiOutput processAuthenticated(SaveSetupStorageApiInput input) {
    SettingsService settings = serviceProvider.getSettingsService();
    String previous = settings.saveSetupStorage(input.getDataFolder(), input.getKeepLogFiles());
    LoggingBootstrap.setKeepLogFiles(input.getKeepLogFiles());
    LOGGER.info("Setup: log retention set to " + (input.getKeepLogFiles() > 0 ? input.getKeepLogFiles() + " day(s)" : "none (no rotation)"));
    if (previous != null) {
      String logFile = new File(settings.getDataFolder(), "logs/log.txt").getAbsolutePath();
      LOGGER.info("Setup: data folder moved from " + previous + " to " + settings.getDataFolder() + "; logging continues in " + logFile);
      LoggingBootstrap.moveTo(logFile);
      LOGGER.info("Setup: data folder is now " + settings.getDataFolder() + ". " + previous
          + " only holds the setup's first log lines and a copy of the web UI: it can be deleted once MOM has been restarted.");
    }
    return new SaveSetupStorageApiOutput(settings.getDataFolder());
  }
}

@TypeScriptType
class SaveSetupStorageApiInput extends AuthenticatedApiInput {
  /** Absolute, or relative to the directory holding config.json. */
  private String dataFolder;
  private int keepLogFiles;

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
}

@TypeScriptType
class SaveSetupStorageApiOutput {
  /** The data folder as an absolute path. */
  private String resolvedDataFolder;

  public SaveSetupStorageApiOutput() {
  }

  public SaveSetupStorageApiOutput(String resolvedDataFolder) {
    this.resolvedDataFolder = resolvedDataFolder;
  }

  public String getResolvedDataFolder() {
    return resolvedDataFolder;
  }

  public void setResolvedDataFolder(String resolvedDataFolder) {
    this.resolvedDataFolder = resolvedDataFolder;
  }
}
