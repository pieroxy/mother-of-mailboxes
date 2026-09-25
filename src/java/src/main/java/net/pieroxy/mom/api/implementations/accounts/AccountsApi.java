package net.pieroxy.mom.api.implementations.accounts;

import net.pieroxy.mom.api.ServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.rules.MailAccount;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/** The live status of every configured {@link MailAccount}, for the logged-in homepage. */
@Endpoint(method = ApiMethod.GET)
public class AccountsApi extends AbstractApiEndpoint<AccountsApiInput, AccountsApiOutput> {
  private final ServiceProvider serviceProvider;

  public AccountsApi(ServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public AccountsApiOutput process(AccountsApiInput input) {
    List<AccountStatusDto> accounts = serviceProvider.getAccounts().stream()
        .map(AccountsApi::toDto)
        .collect(Collectors.toList());
    return new AccountsApiOutput(accounts);
  }

  private static AccountStatusDto toDto(MailAccount account) {
    Instant lastErrorTimestamp = account.getLastErrorTimestamp();
    return new AccountStatusDto(account.getAccountLabel(), account.getStatus().name(),
        account.getMessagesProcessed(), account.getMessagesMatched(),
        account.getLastErrorMessage(), lastErrorTimestamp != null ? lastErrorTimestamp.toString() : null);
  }
}

@TypeScriptType
class AccountsApiInput {
}

@TypeScriptType
class AccountsApiOutput {
  private List<AccountStatusDto> accounts;

  public AccountsApiOutput() {
  }

  public AccountsApiOutput(List<AccountStatusDto> accounts) {
    this.accounts = accounts;
  }

  public List<AccountStatusDto> getAccounts() {
    return accounts;
  }

  public void setAccounts(List<AccountStatusDto> accounts) {
    this.accounts = accounts;
  }
}

@TypeScriptType
class AccountStatusDto {
  private String name;
  private String status;
  private long messagesProcessed;
  private long messagesMatched;
  /** Message of the account's most recent cycle failure this session, or null if none happened. */
  private String lastErrorMessage;
  /** ISO-8601 timestamp of {@link #lastErrorMessage}, or null if none happened this session. */
  private String lastErrorTimestamp;

  public AccountStatusDto() {
  }

  public AccountStatusDto(String name, String status, long messagesProcessed, long messagesMatched,
                           String lastErrorMessage, String lastErrorTimestamp) {
    this.name = name;
    this.status = status;
    this.messagesProcessed = messagesProcessed;
    this.messagesMatched = messagesMatched;
    this.lastErrorMessage = lastErrorMessage;
    this.lastErrorTimestamp = lastErrorTimestamp;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public long getMessagesProcessed() {
    return messagesProcessed;
  }

  public void setMessagesProcessed(long messagesProcessed) {
    this.messagesProcessed = messagesProcessed;
  }

  public long getMessagesMatched() {
    return messagesMatched;
  }

  public void setMessagesMatched(long messagesMatched) {
    this.messagesMatched = messagesMatched;
  }

  public String getLastErrorMessage() {
    return lastErrorMessage;
  }

  public void setLastErrorMessage(String lastErrorMessage) {
    this.lastErrorMessage = lastErrorMessage;
  }

  public String getLastErrorTimestamp() {
    return lastErrorTimestamp;
  }

  public void setLastErrorTimestamp(String lastErrorTimestamp) {
    this.lastErrorTimestamp = lastErrorTimestamp;
  }
}
