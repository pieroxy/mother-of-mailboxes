package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.api.metadata.TypeScriptType;

/** One thing a check verified, and how it went — e.g. "The record ends with -all". */
@TypeScriptType
public class CheckFindingDto {
  private CheckStatus status;
  private String message;

  public CheckFindingDto() {
  }

  public CheckFindingDto(CheckStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  public CheckStatus getStatus() {
    return status;
  }

  public void setStatus(CheckStatus status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
