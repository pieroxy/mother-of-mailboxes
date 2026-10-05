package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.LearningShortcutConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import net.pieroxy.mom.learning.RuleLearner;
import net.pieroxy.mom.rules.RuleCatalog;

import java.util.List;

/**
 * Saves everything the account settings page lets you edit — Config, Credentials, Rules and
 * Shortcuts — in one call, and restarts the account once. The webapp stages every edit locally
 * (see {@code AccountEditSession.ts}) while the user reviews them on the read-only settings page,
 * and only calls this endpoint when they click "Save Changes"; Cancel/Discard never reaches the
 * server at all. Replaces the old one-endpoint-per-section calls (UpdateAccountConfig/
 * UpdateAccountCredentials/UpdateAccountRules), which each restarted the account on their own.
 */
@Endpoint(method = ApiMethod.POST)
public class UpdateAccountApi extends AbstractAuthenticatedEndpoint<UpdateAccountApiInput, UpdateAccountApiOutput> {
  public UpdateAccountApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public UpdateAccountApiOutput processAuthenticated(UpdateAccountApiInput input) {
    if (input.getHost() == null || input.getHost().isBlank()) {
      throw new IllegalArgumentException("Host must not be blank.");
    }
    if (input.getPort() <= 0 || input.getPort() > 65535) {
      throw new IllegalArgumentException("Port must be between 1 and 65535.");
    }
    if (input.getRunEvery() <= 0) {
      throw new IllegalArgumentException("\"Run every\" must be a positive number of seconds.");
    }
    if (input.getConnectTimeout() < 0 || input.getReadTimeout() < 0) {
      throw new IllegalArgumentException("Timeouts must not be negative.");
    }
    if (input.getUsername() == null || input.getUsername().isBlank()) {
      throw new IllegalArgumentException("Username must not be blank.");
    }
    if (input.getRules() == null) {
      throw new IllegalArgumentException("rules must not be null.");
    }
    RuleCatalog.validateRules(input.getRules());
    List<LearningShortcutConfiguration> shortcuts = input.getShortcuts() != null ? input.getShortcuts() : List.of();
    RuleLearner.validateShortcuts(shortcuts);

    serviceProvider.getAccountService().updateAccount(input.getAccountName(), input.getHost(), input.getPort(), input.getRunEvery(),
        input.getConnectTimeout(), input.getReadTimeout(),
        blankToNull(input.getClassifierSpamFolderName()),
        input.getClassifierExcludedFolders() != null ? input.getClassifierExcludedFolders() : List.of(),
        input.getClassifierCorpusRetentionDays(), input.getClassifierCorpusScanBatchSize(),
        input.isDiscoveryTreeDisabled(), input.getUsername(), input.getPassword(), input.getRules(), shortcuts);

    return new UpdateAccountApiOutput();
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}

@TypeScriptType
class UpdateAccountApiInput extends AuthenticatedApiInput {
  private String accountName;
  private String host;
  private int port;
  private int runEvery;
  private int connectTimeout;
  private int readTimeout;
  private String classifierSpamFolderName;
  private List<String> classifierExcludedFolders;
  private int classifierCorpusRetentionDays;
  private int classifierCorpusScanBatchSize;
  private boolean discoveryTreeDisabled;
  private String username;
  /** Blank means "leave the current password unchanged" — see the class javadoc. */
  private String password;
  private List<MailFilterRuleConfiguration> rules;
  private List<LearningShortcutConfiguration> shortcuts;

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
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

  public List<String> getClassifierExcludedFolders() {
    return classifierExcludedFolders;
  }

  public void setClassifierExcludedFolders(List<String> classifierExcludedFolders) {
    this.classifierExcludedFolders = classifierExcludedFolders;
  }

  public int getClassifierCorpusRetentionDays() {
    return classifierCorpusRetentionDays;
  }

  public void setClassifierCorpusRetentionDays(int classifierCorpusRetentionDays) {
    this.classifierCorpusRetentionDays = classifierCorpusRetentionDays;
  }

  public int getClassifierCorpusScanBatchSize() {
    return classifierCorpusScanBatchSize;
  }

  public void setClassifierCorpusScanBatchSize(int classifierCorpusScanBatchSize) {
    this.classifierCorpusScanBatchSize = classifierCorpusScanBatchSize;
  }

  public boolean isDiscoveryTreeDisabled() {
    return discoveryTreeDisabled;
  }

  public void setDiscoveryTreeDisabled(boolean discoveryTreeDisabled) {
    this.discoveryTreeDisabled = discoveryTreeDisabled;
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

  public List<MailFilterRuleConfiguration> getRules() {
    return rules;
  }

  public void setRules(List<MailFilterRuleConfiguration> rules) {
    this.rules = rules;
  }

  public List<LearningShortcutConfiguration> getShortcuts() {
    return shortcuts;
  }

  public void setShortcuts(List<LearningShortcutConfiguration> shortcuts) {
    this.shortcuts = shortcuts;
  }
}

@TypeScriptType
class UpdateAccountApiOutput {
}
