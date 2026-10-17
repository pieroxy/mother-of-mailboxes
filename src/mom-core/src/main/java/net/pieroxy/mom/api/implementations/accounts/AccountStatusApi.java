package net.pieroxy.mom.api.implementations.accounts;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.SmtpConfiguration;
import net.pieroxy.mom.rules.MailAccount;

/**
 * The live status of one {@link MailAccount} — the same {@link AccountStatusDto} as
 * {@link AccountsApi}'s, plus the bits of its configuration worth showing on its home page.
 */
@Endpoint(method = ApiMethod.GET)
public class AccountStatusApi extends AbstractAuthenticatedEndpoint<AccountStatusApiInput, AccountStatusApiOutput> {
  public AccountStatusApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public AccountStatusApiOutput processAuthenticated(AccountStatusApiInput input) {
    MailAccount account = serviceProvider.getAccountService().getAccounts().stream()
        .filter(a -> a.getAccountLabel().equals(input.getAccountName()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No such account: " + input.getAccountName()));

    MailAccountConfiguration config = account.getConfig();
    SmtpConfiguration smtp = config.getSmtp();
    AccountStatusApiOutput output = new AccountStatusApiOutput(AccountsApi.toDto(account), config.getHost(), config.getPort(), config.getRunEvery(),
        smtp == null ? null : smtp.getHost(), smtp == null ? 0 : smtp.getPort(), smtp == null ? null : smtp.getFromAddress());
    output.setMailDiagnosticsConfigured(serviceProvider.getSettingsService().getConfiguration().getMailDiagnosticsSinkUrl() != null);
    return output;
  }
}

@TypeScriptType
class AccountStatusApiInput extends AuthenticatedApiInput {
  private String accountName;

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }
}

@TypeScriptType
class AccountStatusApiOutput {
  private AccountStatusDto account;
  private String imapHost;
  private int imapPort;
  /** Seconds between two cycles. */
  private int runEvery;
  /** Null if the account has no SMTP settings (it can't send mail). */
  private String smtpHost;
  /** 0 if the account has no SMTP settings. */
  private int smtpPort;
  /** Null if the account has no SMTP settings. */
  private String smtpFromAddress;
  /** Whether an SMTP sink is configured server-wide (General Settings) — see MailDiagnosticsConfiguration. */
  private boolean mailDiagnosticsConfigured;

  public AccountStatusApiOutput() {
  }

  public AccountStatusApiOutput(AccountStatusDto account, String imapHost, int imapPort, int runEvery,
                                String smtpHost, int smtpPort, String smtpFromAddress) {
    this.account = account;
    this.imapHost = imapHost;
    this.imapPort = imapPort;
    this.runEvery = runEvery;
    this.smtpHost = smtpHost;
    this.smtpPort = smtpPort;
    this.smtpFromAddress = smtpFromAddress;
  }

  public AccountStatusDto getAccount() {
    return account;
  }

  public void setAccount(AccountStatusDto account) {
    this.account = account;
  }

  public String getImapHost() {
    return imapHost;
  }

  public void setImapHost(String imapHost) {
    this.imapHost = imapHost;
  }

  public int getImapPort() {
    return imapPort;
  }

  public void setImapPort(int imapPort) {
    this.imapPort = imapPort;
  }

  public int getRunEvery() {
    return runEvery;
  }

  public void setRunEvery(int runEvery) {
    this.runEvery = runEvery;
  }

  public String getSmtpHost() {
    return smtpHost;
  }

  public void setSmtpHost(String smtpHost) {
    this.smtpHost = smtpHost;
  }

  public int getSmtpPort() {
    return smtpPort;
  }

  public void setSmtpPort(int smtpPort) {
    this.smtpPort = smtpPort;
  }

  public String getSmtpFromAddress() {
    return smtpFromAddress;
  }

  public void setSmtpFromAddress(String smtpFromAddress) {
    this.smtpFromAddress = smtpFromAddress;
  }

  public boolean isMailDiagnosticsConfigured() {
    return mailDiagnosticsConfigured;
  }

  public void setMailDiagnosticsConfigured(boolean mailDiagnosticsConfigured) {
    this.mailDiagnosticsConfigured = mailDiagnosticsConfigured;
  }
}
