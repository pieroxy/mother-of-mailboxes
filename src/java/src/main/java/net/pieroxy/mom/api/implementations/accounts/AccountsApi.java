package net.pieroxy.mom.api.implementations.accounts;

import net.pieroxy.mom.api.ServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.detection.classifier.BodyClassifierTrainer;
import net.pieroxy.mom.detection.classifier.ClassifierTrainingCounts;
import net.pieroxy.mom.detection.classifier.HeaderClassifierTrainer;
import net.pieroxy.mom.detection.classifier.SubjectClassifierTrainer;
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
    return new AccountStatusDto(account.getAccountLabel(), account.getStatus().name(),
        account.getMessagesProcessed(), account.getMessagesMatched(),
        account.getLastErrorMessage(), toIsoString(account.getLastErrorTimestamp()),
        toIsoString(account.getLastCycleCompletedTimestamp()), toIsoString(account.getNextScheduledCycle()),
        toClassifierTrainingDto(account), account.getRuleCount(), account.getActiveRuleCount());
  }

  private static ClassifierTrainingStateDto toClassifierTrainingDto(MailAccount account) {
    ClassifierTrainingCounts counts = account.getClassifierTrainingCounts();
    return new ClassifierTrainingStateDto(counts.getSpamCount(), counts.getHamCount(),
        new ClassifierTrainingDto(account.isSubjectClassifierTrained(),
            toIsoString(account.getSubjectClassifierTrainedTimestamp()), SubjectClassifierTrainer.getMinExamplesPerClass()),
        new ClassifierTrainingDto(account.isHeaderClassifierTrained(),
            toIsoString(account.getHeaderClassifierTrainedTimestamp()), HeaderClassifierTrainer.getMinExamplesPerClass()),
        new ClassifierTrainingDto(account.isBodyClassifierTrained(),
            toIsoString(account.getBodyClassifierTrainedTimestamp()), BodyClassifierTrainer.getMinExamplesPerClass()));
  }

  private static String toIsoString(Instant instant) {
    return instant == null ? null : instant.toString();
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
  /** ISO-8601 timestamp of the last cycle (success or failure) to finish, or null before the first one has. */
  private String lastCycleCompletedTimestamp;
  /** ISO-8601 best-effort estimate of when the next cycle will start, or null before the first one has completed. */
  private String nextScheduledCycleTimestamp;
  private ClassifierTrainingStateDto classifierTraining;
  /** Number of manually configured rules for this account (config.json's "rules" list). */
  private int ruleCount;
  /** Of those, how many have an action that isn't NOOP (a LEARNED_RULES entry, with no action of its own, doesn't count). */
  private int activeRuleCount;

  public AccountStatusDto() {
  }

  public AccountStatusDto(String name, String status, long messagesProcessed, long messagesMatched,
                           String lastErrorMessage, String lastErrorTimestamp,
                           String lastCycleCompletedTimestamp, String nextScheduledCycleTimestamp,
                           ClassifierTrainingStateDto classifierTraining, int ruleCount, int activeRuleCount) {
    this.name = name;
    this.status = status;
    this.messagesProcessed = messagesProcessed;
    this.messagesMatched = messagesMatched;
    this.lastErrorMessage = lastErrorMessage;
    this.lastErrorTimestamp = lastErrorTimestamp;
    this.lastCycleCompletedTimestamp = lastCycleCompletedTimestamp;
    this.nextScheduledCycleTimestamp = nextScheduledCycleTimestamp;
    this.classifierTraining = classifierTraining;
    this.ruleCount = ruleCount;
    this.activeRuleCount = activeRuleCount;
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

  public String getLastCycleCompletedTimestamp() {
    return lastCycleCompletedTimestamp;
  }

  public void setLastCycleCompletedTimestamp(String lastCycleCompletedTimestamp) {
    this.lastCycleCompletedTimestamp = lastCycleCompletedTimestamp;
  }

  public String getNextScheduledCycleTimestamp() {
    return nextScheduledCycleTimestamp;
  }

  public void setNextScheduledCycleTimestamp(String nextScheduledCycleTimestamp) {
    this.nextScheduledCycleTimestamp = nextScheduledCycleTimestamp;
  }

  public ClassifierTrainingStateDto getClassifierTraining() {
    return classifierTraining;
  }

  public void setClassifierTraining(ClassifierTrainingStateDto classifierTraining) {
    this.classifierTraining = classifierTraining;
  }

  public int getRuleCount() {
    return ruleCount;
  }

  public void setRuleCount(int ruleCount) {
    this.ruleCount = ruleCount;
  }

  public int getActiveRuleCount() {
    return activeRuleCount;
  }

  public void setActiveRuleCount(int activeRuleCount) {
    this.activeRuleCount = activeRuleCount;
  }
}

/**
 * An account's classifier training state: {@link #spamExamples}/{@link #hamExamples} are shared
 * by all three classifiers (they train on the same corpus, see
 * {@code ClassifierTrainingCounts}) — {@link #subject}, {@link #header} and {@link #body} each
 * carry their own trained/lastTrainedTimestamp/threshold since they train independent models.
 */
@TypeScriptType
class ClassifierTrainingStateDto {
  private long spamExamples;
  private long hamExamples;
  private ClassifierTrainingDto subject;
  private ClassifierTrainingDto header;
  private ClassifierTrainingDto body;

  public ClassifierTrainingStateDto() {
  }

  public ClassifierTrainingStateDto(long spamExamples, long hamExamples, ClassifierTrainingDto subject,
                                     ClassifierTrainingDto header, ClassifierTrainingDto body) {
    this.spamExamples = spamExamples;
    this.hamExamples = hamExamples;
    this.subject = subject;
    this.header = header;
    this.body = body;
  }

  public long getSpamExamples() {
    return spamExamples;
  }

  public void setSpamExamples(long spamExamples) {
    this.spamExamples = spamExamples;
  }

  public long getHamExamples() {
    return hamExamples;
  }

  public void setHamExamples(long hamExamples) {
    this.hamExamples = hamExamples;
  }

  public ClassifierTrainingDto getSubject() {
    return subject;
  }

  public void setSubject(ClassifierTrainingDto subject) {
    this.subject = subject;
  }

  public ClassifierTrainingDto getHeader() {
    return header;
  }

  public void setHeader(ClassifierTrainingDto header) {
    this.header = header;
  }

  public ClassifierTrainingDto getBody() {
    return body;
  }

  public void setBody(ClassifierTrainingDto body) {
    this.body = body;
  }
}

/** One classifier's (subject/header/body) training status — see {@link ClassifierTrainingStateDto}. */
@TypeScriptType
class ClassifierTrainingDto {
  private boolean trained;
  /** ISO-8601 timestamp of the model file's last (re)training, or null if it's never been trained. */
  private String lastTrainedTimestamp;
  /** Examples needed of each class (see {@code ClassifierTrainingStateDto.spamExamples}/{@code hamExamples}) before this classifier (re)trains. */
  private int minExamplesPerClass;

  public ClassifierTrainingDto() {
  }

  public ClassifierTrainingDto(boolean trained, String lastTrainedTimestamp, int minExamplesPerClass) {
    this.trained = trained;
    this.lastTrainedTimestamp = lastTrainedTimestamp;
    this.minExamplesPerClass = minExamplesPerClass;
  }

  public boolean isTrained() {
    return trained;
  }

  public void setTrained(boolean trained) {
    this.trained = trained;
  }

  public String getLastTrainedTimestamp() {
    return lastTrainedTimestamp;
  }

  public void setLastTrainedTimestamp(String lastTrainedTimestamp) {
    this.lastTrainedTimestamp = lastTrainedTimestamp;
  }

  public int getMinExamplesPerClass() {
    return minExamplesPerClass;
  }

  public void setMinExamplesPerClass(int minExamplesPerClass) {
    this.minExamplesPerClass = minExamplesPerClass;
  }
}
