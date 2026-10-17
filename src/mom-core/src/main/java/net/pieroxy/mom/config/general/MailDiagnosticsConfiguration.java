package net.pieroxy.mom.config.general;

/** Server-wide settings of the mail server diagnostics tools. */
public class MailDiagnosticsConfiguration {
  /**
   * Base URL of the SMTP sink test messages are sent to and read back from (e.g.
   * {@code https://smtp-sink.mom.pieroxy.net}), without a trailing slash — see
   * {@code SmtpSinkClient}. Absent or blank: the diagnostics needing it are disabled.
   */
  private String sinkUrl;

  public String getSinkUrl() {
    return sinkUrl;
  }

  public void setSinkUrl(String sinkUrl) {
    this.sinkUrl = sinkUrl;
  }
}
