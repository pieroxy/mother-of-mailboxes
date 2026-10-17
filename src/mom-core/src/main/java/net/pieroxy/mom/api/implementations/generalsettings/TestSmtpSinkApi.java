package net.pieroxy.mom.api.implementations.generalsettings;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.diagnostics.SmtpSinkClient;
import net.pieroxy.mom.services.IServiceProvider;

import java.io.IOException;

/**
 * Checks that the SMTP sink URL being edited on the General Settings page (saved or not) answers
 * like one, by fetching its {@code /info}. Same as the IMAP/SMTP connection tests, a failure is an
 * expected outcome of testing, reported in the output rather than thrown.
 */
@Endpoint(method = ApiMethod.POST)
public class TestSmtpSinkApi extends AbstractAuthenticatedEndpoint<TestSmtpSinkApiInput, TestSmtpSinkApiOutput> {
  public TestSmtpSinkApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public TestSmtpSinkApiOutput processAuthenticated(TestSmtpSinkApiInput input) {
    String url;
    try {
      url = SmtpSinkClient.normalizeUrl(input.getSinkUrl());
    } catch (IllegalArgumentException e) {
      return new TestSmtpSinkApiOutput(false, e.getMessage());
    }
    if (url == null) return new TestSmtpSinkApiOutput(false, "No SMTP sink URL to test.");
    try {
      SmtpSinkClient.SinkInfo info = SmtpSinkClient.fetchInfo(url);
      return new TestSmtpSinkApiOutput(true, "Connected: test messages will be sent to " + info.mailDomain()
          + " (up to " + info.maxMessageSizeBytes() / 1024 + " KB, kept " + info.retentionSeconds() + "s).");
    } catch (IOException e) {
      return new TestSmtpSinkApiOutput(false, "Could not reach the SMTP sink: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new TestSmtpSinkApiOutput(false, "Interrupted.");
    }
  }
}

@TypeScriptType
class TestSmtpSinkApiInput extends AuthenticatedApiInput {
  private String sinkUrl;

  public String getSinkUrl() {
    return sinkUrl;
  }

  public void setSinkUrl(String sinkUrl) {
    this.sinkUrl = sinkUrl;
  }
}

@TypeScriptType
class TestSmtpSinkApiOutput {
  private boolean connected;
  private String message;

  public TestSmtpSinkApiOutput() {
  }

  public TestSmtpSinkApiOutput(boolean connected, String message) {
    this.connected = connected;
    this.message = message;
  }

  public boolean isConnected() {
    return connected;
  }

  public void setConnected(boolean connected) {
    this.connected = connected;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
