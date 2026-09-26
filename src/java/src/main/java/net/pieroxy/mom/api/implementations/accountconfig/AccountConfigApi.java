package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.api.ServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.LearningShortcutConfiguration;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import net.pieroxy.mom.rules.MailAccount;

import java.util.List;

/**
 * One account's full configuration, read-only, for its dedicated settings page — everything
 * config.json holds for it except the actual credentials (see {@link CredentialsInfoDto}: only
 * the credentials key and the resolved username are shown, never the password).
 */
@Endpoint(method = ApiMethod.GET)
public class AccountConfigApi extends AbstractApiEndpoint<AccountConfigApiInput, AccountConfigApiOutput> {
  private final ServiceProvider serviceProvider;

  public AccountConfigApi(ServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public AccountConfigApiOutput process(AccountConfigApiInput input) {
    MailAccount account = serviceProvider.getAccounts().stream()
        .filter(a -> a.getAccountLabel().equals(input.getAccountName()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No such account: " + input.getAccountName()));

    MailAccountConfiguration config = account.getConfig();

    AccountBasicConfigDto basicConfig = new AccountBasicConfigDto(config.getDisplayName(), config.getHost(), config.getPort(),
        config.getRunEvery(), config.getClassifierSpamFolderName(), config.getClassifierExcludedFolders(),
        config.getClassifierCorpusRetentionDays(), config.getClassifierCorpusScanBatchSize(), config.isDiscoveryTreeDisabled());

    CredentialsInfoDto credentials = new CredentialsInfoDto(config.getCredentials(), account.getCredentialUsername());

    List<MailFilterRuleConfiguration> rules = config.getRules() != null ? config.getRules() : List.of();
    List<LearningShortcutConfiguration> shortcuts = config.getLearningShortcuts() != null ? config.getLearningShortcuts() : List.of();

    return new AccountConfigApiOutput(basicConfig, credentials, rules, shortcuts);
  }
}

@TypeScriptType
class AccountConfigApiInput {
  private String accountName;

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }
}

@TypeScriptType
class AccountConfigApiOutput {
  private AccountBasicConfigDto config;
  private CredentialsInfoDto credentials;
  private List<MailFilterRuleConfiguration> rules;
  private List<LearningShortcutConfiguration> shortcuts;

  public AccountConfigApiOutput() {
  }

  public AccountConfigApiOutput(AccountBasicConfigDto config, CredentialsInfoDto credentials,
                                 List<MailFilterRuleConfiguration> rules, List<LearningShortcutConfiguration> shortcuts) {
    this.config = config;
    this.credentials = credentials;
    this.rules = rules;
    this.shortcuts = shortcuts;
  }

  public AccountBasicConfigDto getConfig() {
    return config;
  }

  public void setConfig(AccountBasicConfigDto config) {
    this.config = config;
  }

  public CredentialsInfoDto getCredentials() {
    return credentials;
  }

  public void setCredentials(CredentialsInfoDto credentials) {
    this.credentials = credentials;
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
class AccountBasicConfigDto {
  private String displayName;
  private String host;
  private int port;
  private int runEvery;
  private String classifierSpamFolderName;
  private List<String> classifierExcludedFolders;
  private int classifierCorpusRetentionDays;
  private int classifierCorpusScanBatchSize;
  private boolean discoveryTreeDisabled;

  public AccountBasicConfigDto() {
  }

  public AccountBasicConfigDto(String displayName, String host, int port, int runEvery, String classifierSpamFolderName,
                                List<String> classifierExcludedFolders, int classifierCorpusRetentionDays,
                                int classifierCorpusScanBatchSize, boolean discoveryTreeDisabled) {
    this.displayName = displayName;
    this.host = host;
    this.port = port;
    this.runEvery = runEvery;
    this.classifierSpamFolderName = classifierSpamFolderName;
    this.classifierExcludedFolders = classifierExcludedFolders != null ? classifierExcludedFolders : List.of();
    this.classifierCorpusRetentionDays = classifierCorpusRetentionDays;
    this.classifierCorpusScanBatchSize = classifierCorpusScanBatchSize;
    this.discoveryTreeDisabled = discoveryTreeDisabled;
  }

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

  public int getRunEvery() {
    return runEvery;
  }

  public void setRunEvery(int runEvery) {
    this.runEvery = runEvery;
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
}

/** Never carries the actual secret (see {@code Credential#getPassword()}) — only what identifies which one is used. */
@TypeScriptType
class CredentialsInfoDto {
  private String credentialsKey;
  private String username;

  public CredentialsInfoDto() {
  }

  public CredentialsInfoDto(String credentialsKey, String username) {
    this.credentialsKey = credentialsKey;
    this.username = username;
  }

  public String getCredentialsKey() {
    return credentialsKey;
  }

  public void setCredentialsKey(String credentialsKey) {
    this.credentialsKey = credentialsKey;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }
}
