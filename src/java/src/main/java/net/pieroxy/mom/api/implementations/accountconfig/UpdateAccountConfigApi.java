package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.api.ServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.MailAccountConfiguration;

import java.util.List;

/**
 * Saves the editable fields of the Config section (see {@code AccountConfigApi}'s
 * {@code AccountBasicConfigDto}) and restarts the account so the change takes effect right away
 * — see {@link ServiceProvider#restartAccount}. Deliberately excludes {@code displayName}: it
 * names every one of this account's on-disk files (state, learned rules, classifier corpus,
 * stats — see {@code FileNameValidator}'s callers), so renaming it here would silently orphan
 * that history instead of migrating it. Credentials, rules and learning shortcuts are edited
 * elsewhere (or not yet, for rules/shortcuts).
 */
@Endpoint(method = ApiMethod.POST)
public class UpdateAccountConfigApi extends AbstractApiEndpoint<UpdateAccountConfigApiInput, UpdateAccountConfigApiOutput> {
  private final ServiceProvider serviceProvider;

  public UpdateAccountConfigApi(ServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public UpdateAccountConfigApiOutput process(UpdateAccountConfigApiInput input) {
    if (input.getHost() == null || input.getHost().isBlank()) {
      throw new IllegalArgumentException("Host must not be blank.");
    }
    if (input.getPort() <= 0 || input.getPort() > 65535) {
      throw new IllegalArgumentException("Port must be between 1 and 65535.");
    }
    if (input.getRunEvery() <= 0) {
      throw new IllegalArgumentException("\"Run every\" must be a positive number of seconds.");
    }

    MailAccountConfiguration config = serviceProvider.findAccountConfig(input.getAccountName());
    config.setHost(input.getHost());
    config.setPort(input.getPort());
    config.setRunEvery(input.getRunEvery());
    config.setClassifierSpamFolderName(blankToNull(input.getClassifierSpamFolderName()));
    config.setClassifierExcludedFolders(input.getClassifierExcludedFolders() != null ? input.getClassifierExcludedFolders() : List.of());
    config.setClassifierCorpusRetentionDays(input.getClassifierCorpusRetentionDays());
    config.setClassifierCorpusScanBatchSize(input.getClassifierCorpusScanBatchSize());
    config.setDiscoveryTreeDisabled(input.isDiscoveryTreeDisabled());

    serviceProvider.restartAccount(input.getAccountName());

    return new UpdateAccountConfigApiOutput();
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}

@TypeScriptType
class UpdateAccountConfigApiInput {
  private String accountName;
  private String host;
  private int port;
  private int runEvery;
  private String classifierSpamFolderName;
  private List<String> classifierExcludedFolders;
  private int classifierCorpusRetentionDays;
  private int classifierCorpusScanBatchSize;
  private boolean discoveryTreeDisabled;

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

@TypeScriptType
class UpdateAccountConfigApiOutput {
}
