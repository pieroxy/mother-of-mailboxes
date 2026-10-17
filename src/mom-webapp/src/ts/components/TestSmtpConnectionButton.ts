import m from "mithril";
import { ApiEndpoints } from "../auto/ApiEndpoints";
import { SmtpSettingsDto } from "../auto/pieroxy-mom";
import { AccountEditSession } from "../utils/AccountEditSession";
import { TestConnectionButton } from "./TestConnectionButton";

interface TestSmtpConnectionButtonAttrs {
  session: AccountEditSession;
  smtp: SmtpSettingsDto;
  /** The SMTP password being edited, used with ownCredentials; blank falls back to a saved one — see TestSmtpConnectionApi. */
  password: string;
}

/**
 * Shared by AccountSettingsPage's SMTP card and AccountSmtpSettingsEditPage. Without own
 * credentials, tests with the IMAP login as currently staged.
 */
export class TestSmtpConnectionButton implements m.ClassComponent<TestSmtpConnectionButtonAttrs> {
  view({ attrs }: m.Vnode<TestSmtpConnectionButtonAttrs>): m.Children {
    const { session, smtp } = attrs;
    return m(TestConnectionButton, {
      label: "Test SMTP Connection",
      run: () => ApiEndpoints.TestSmtpConnection.call({
        accountName: session.accountName,
        host: smtp.host,
        port: smtp.port,
        security: smtp.security,
        connectTimeout: session.workingConfig.connectTimeout,
        readTimeout: session.workingConfig.readTimeout,
        ownCredentials: smtp.ownCredentials,
        username: smtp.ownCredentials ? smtp.username : session.workingUsername,
        password: smtp.ownCredentials ? attrs.password : session.workingPassword,
      }),
    });
  }
}
