package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.api.metadata.TypeScriptType;

import java.util.ArrayList;
import java.util.List;

/**
 * The result of one {@link DeliveryCheck}, in a shape shared by every check so the webapp renders
 * them all the same way: an overall {@link #status} and one-line {@link #summary}, what was looked
 * at ({@link #facts}), and what was verified ({@link #findings}, each with its own status).
 */
@TypeScriptType
public class DeliveryCheckResultDto {
  private DeliveryCheck check;
  private CheckStatus status;
  private String summary;
  private List<CheckFactDto> facts = new ArrayList<>();
  private List<CheckFindingDto> findings = new ArrayList<>();

  public DeliveryCheckResultDto() {
  }

  public DeliveryCheckResultDto(DeliveryCheck check) {
    this.check = check;
  }

  /** A check that couldn't run at all — e.g. no SMTP settings to take the domain from. */
  public static DeliveryCheckResultDto skipped(DeliveryCheck check, String reason) {
    DeliveryCheckResultDto result = new DeliveryCheckResultDto(check);
    result.status = CheckStatus.SKIPPED;
    result.summary = reason;
    return result;
  }

  public DeliveryCheckResultDto fact(String label, String value) {
    facts.add(new CheckFactDto(label, value));
    return this;
  }

  public DeliveryCheckResultDto finding(CheckStatus status, String message) {
    findings.add(new CheckFindingDto(status, message));
    return this;
  }

  /** Sets {@link #status} from the findings (see {@link CheckStatus#worstOf}) and the summary that goes with it. */
  public DeliveryCheckResultDto conclude(String okSummary) {
    status = CheckStatus.worstOf(findings);
    summary = switch (status) {
      case ERROR, WARNING -> findings.stream().filter(f -> f.getStatus() == status).findFirst().map(CheckFindingDto::getMessage).orElse(okSummary);
      default -> okSummary;
    };
    return this;
  }

  public DeliveryCheck getCheck() {
    return check;
  }

  public void setCheck(DeliveryCheck check) {
    this.check = check;
  }

  public CheckStatus getStatus() {
    return status;
  }

  public void setStatus(CheckStatus status) {
    this.status = status;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public List<CheckFactDto> getFacts() {
    return facts;
  }

  public void setFacts(List<CheckFactDto> facts) {
    this.facts = facts;
  }

  public List<CheckFindingDto> getFindings() {
    return findings;
  }

  public void setFindings(List<CheckFindingDto> findings) {
    this.findings = findings;
  }
}
