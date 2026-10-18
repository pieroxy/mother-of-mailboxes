package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.api.metadata.TypeScriptType;

/** Something a check looked at, shown as is — e.g. "Record: v=spf1 mx -all". */
@TypeScriptType
public class CheckFactDto {
  private String label;
  private String value;

  public CheckFactDto() {
  }

  public CheckFactDto(String label, String value) {
    this.label = label;
    this.value = value;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
