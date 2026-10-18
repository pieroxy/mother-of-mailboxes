package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.api.metadata.TypeScriptType;

/**
 * Outcome of one delivery check, or of one finding within it (see {@link DeliveryCheckResultDto}).
 * {@link #IN_PROGRESS} is never returned by the server: the webapp shows it while a check runs.
 */
@TypeScriptType
public enum CheckStatus {
  OK,
  WARNING,
  ERROR,
  INFO,
  SKIPPED,
  IN_PROGRESS;

  /**
   * The overall status of a check from its findings: the worst of them — ERROR, then WARNING, then
   * OK — or INFO if all of them are informational (and SKIPPED if there are none).
   */
  public static CheckStatus worstOf(Iterable<CheckFindingDto> findings) {
    boolean any = false, ok = false, warning = false;
    for (CheckFindingDto f : findings) {
      any = true;
      if (f.getStatus() == ERROR) return ERROR;
      if (f.getStatus() == WARNING) warning = true;
      if (f.getStatus() == OK) ok = true;
    }
    if (warning) return WARNING;
    if (ok) return OK;
    return any ? INFO : SKIPPED;
  }
}
