package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.ReputationListConfig;
import net.pieroxy.mom.rules.DefaultSpamRules;
import net.pieroxy.mom.services.IServiceProvider;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Creates and starts a new account — the last step of the webapp's account creation wizard. */
@Endpoint(method = ApiMethod.POST)
public class CreateAccountApi extends AbstractAuthenticatedEndpoint<CreateAccountApiInput, CreateAccountApiOutput> {
  private final static String DEFAULT_SPAM_FOLDER = "Spam";

  public CreateAccountApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public CreateAccountApiOutput processAuthenticated(CreateAccountApiInput input) {
    if (isBlank(input.getHost())) {
      throw new IllegalArgumentException("Host must not be blank.");
    }
    if (input.getPort() <= 0 || input.getPort() > 65535) {
      throw new IllegalArgumentException("Port must be between 1 and 65535.");
    }
    if (isBlank(input.getUsername()) || isBlank(input.getPassword())) {
      throw new IllegalArgumentException("Username and password must not be blank.");
    }
    if (input.getRunEvery() <= 0) {
      throw new IllegalArgumentException("\"Run every\" must be a positive number of seconds.");
    }
    if (input.getConnectTimeout() < 0 || input.getReadTimeout() < 0) {
      throw new IllegalArgumentException("Timeouts must not be negative.");
    }

    String spamFolder = spamFolderOrDefault(input.getClassifierSpamFolderName());
    MailAccountConfiguration config = new MailAccountConfiguration();
    config.setDisplayName(input.getDisplayName() != null ? input.getDisplayName().trim() : null);
    config.setHost(input.getHost().trim());
    config.setPort(input.getPort());
    config.setRunEvery(input.getRunEvery());
    config.setConnectTimeout(input.getConnectTimeout());
    config.setReadTimeout(input.getReadTimeout());
    config.setClassifierSpamFolderName(isBlank(input.getClassifierSpamFolderName()) ? null : spamFolder);
    config.setClassifierExcludedFolders(List.of());
    config.setRules(List.of());

    if (input.isHandleSpam()) {
      String classifierFolder = input.getClassifierFolderName() != null ? input.getClassifierFolderName().trim() : "";
      if (classifierFolder.isEmpty()) {
        throw new IllegalArgumentException("The classifier folder must not be blank.");
      }
      if (classifierFolder.equals(spamFolder)) {
        throw new IllegalArgumentException("The classifier folder must differ from the spam folder.");
      }
      if (input.getClassifierCorpusRetentionDays() <= 0) {
        throw new IllegalArgumentException("The classifier corpus retention must be a positive number of days.");
      }
      config.setClassifierExcludedFolders(List.of(classifierFolder));
      config.setClassifierCorpusRetentionDays(input.getClassifierCorpusRetentionDays());
      config.setRules(DefaultSpamRules.build(spamFolder, classifierFolder, configuredListIds(serviceProvider)));
    }

    serviceProvider.getAccountService().createAccount(config, input.getUsername().trim(), input.getPassword());
    return new CreateAccountApiOutput();
  }

  static String spamFolderOrDefault(String spamFolder) {
    return isBlank(spamFolder) ? DEFAULT_SPAM_FOLDER : spamFolder.trim();
  }

  static Set<String> configuredListIds(IServiceProvider serviceProvider) {
    List<ReputationListConfig> lists = serviceProvider.getSettingsService().getConfiguration().getReputationLists();
    return lists == null ? Set.of() : lists.stream().map(ReputationListConfig::getId).collect(Collectors.toSet());
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}

@TypeScriptType
class CreateAccountApiInput extends AuthenticatedApiInput {
  private String displayName;
  private String host;
  private int port;
  private String username;
  private String password;
  private int runEvery;
  private int connectTimeout;
  private int readTimeout;
  private String classifierSpamFolderName;
  /** Generates {@link DefaultSpamRules} for this account. */
  private boolean handleSpam;
  /** Only used with {@link #handleSpam}: receives the subject classifier's verdicts, excluded from the classifier corpus. */
  private String classifierFolderName;
  private int classifierCorpusRetentionDays;

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public int getRunEvery() {
    return runEvery;
  }

  public void setRunEvery(int runEvery) {
    this.runEvery = runEvery;
  }

  public int getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(int connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public int getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(int readTimeout) {
    this.readTimeout = readTimeout;
  }

  public String getClassifierSpamFolderName() {
    return classifierSpamFolderName;
  }

  public void setClassifierSpamFolderName(String classifierSpamFolderName) {
    this.classifierSpamFolderName = classifierSpamFolderName;
  }

  public boolean isHandleSpam() {
    return handleSpam;
  }

  public void setHandleSpam(boolean handleSpam) {
    this.handleSpam = handleSpam;
  }

  public String getClassifierFolderName() {
    return classifierFolderName;
  }

  public void setClassifierFolderName(String classifierFolderName) {
    this.classifierFolderName = classifierFolderName;
  }

  public int getClassifierCorpusRetentionDays() {
    return classifierCorpusRetentionDays;
  }

  public void setClassifierCorpusRetentionDays(int classifierCorpusRetentionDays) {
    this.classifierCorpusRetentionDays = classifierCorpusRetentionDays;
  }
}

@TypeScriptType
class CreateAccountApiOutput {
}
