import m from "mithril";
import { ApiEndpoints } from "../auto/ApiEndpoints";
import { SmtpSettingsDto } from "../auto/pieroxy-mom";
import { TestConnectionButton } from "./TestConnectionButton";

interface TestSmtpConnectionButtonAttrs {
  /** Lets a blank password fall back to the saved one — see TestSmtpConnectionApi. Empty for an account not created yet. */
  accountName: string;
  smtp: SmtpSettingsDto;
  /** The SMTP password being edited, used with ownCredentials. */
  password: string;
  /** Used without ownCredentials. */
  imapUsername: string;
  imapPassword: string;
  connectTimeout: number;
  readTimeout: number;
  onResult?: (connected: boolean) => void;
}

/** Shared by AccountSettingsPage's SMTP card, AccountSmtpSettingsEditPage and the account creation wizard. */
export class TestSmtpConnectionButton implements m.ClassComponent<TestSmtpConnectionButtonAttrs> {
  view({ attrs }: m.Vnode<TestSmtpConnectionButtonAttrs>): m.Children {
    const { smtp } = attrs;
    return m(TestConnectionButton, {
      label: "Test SMTP Connection",
      run: () => ApiEndpoints.TestSmtpConnection.call({
        accountName: attrs.accountName,
        host: smtp.host,
        port: smtp.port,
        security: smtp.security,
        connectTimeout: attrs.connectTimeout,
        readTimeout: attrs.readTimeout,
        ownCredentials: smtp.ownCredentials,
        username: smtp.ownCredentials ? smtp.username : attrs.imapUsername,
        password: smtp.ownCredentials ? attrs.password : attrs.imapPassword,
      }),
      onResult: attrs.onResult,
    });
  }
}
