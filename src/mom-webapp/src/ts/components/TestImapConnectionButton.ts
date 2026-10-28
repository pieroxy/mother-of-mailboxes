import m from "mithril";
import { ApiEndpoints } from "../auto/ApiEndpoints";
import { TestConnectionButton } from "./TestConnectionButton";

interface TestImapConnectionButtonAttrs {
  accountName: string;
  host: string;
  port: number;
  connectTimeout: number;
  readTimeout: number;
  username: string;
  /** Blank, without oauthSignInId, falls back to the account's saved login server-side — see TestImapConnectionApi. */
  password: string;
  /** A completed Microsoft sign-in, used instead of the password. Empty: none. */
  oauthSignInId: string;
  onResult?: (connected: boolean) => void;
}

/**
 * Shared by AccountSettingsPage's read-only IMAP settings card and AccountImapSettingsEditPage's
 * form — both read the exact same staged values (AccountEditSession.workingConfig/workingUsername/
 * workingPassword), so testing from either place tests the same thing.
 * AccountCreatePage also uses it, passing onResult to require a successful test.
 */
export class TestImapConnectionButton implements m.ClassComponent<TestImapConnectionButtonAttrs> {
  view({ attrs }: m.Vnode<TestImapConnectionButtonAttrs>): m.Children {
    return m(TestConnectionButton, {
      label: "Test IMAP Connection",
      onResult: attrs.onResult,
      run: () => ApiEndpoints.TestImapConnection.call({
        accountName: attrs.accountName,
        host: attrs.host,
        port: attrs.port,
        connectTimeout: attrs.connectTimeout,
        readTimeout: attrs.readTimeout,
        username: attrs.username,
        password: attrs.password,
        oauthSignInId: attrs.oauthSignInId,
      }),
    });
  }
}
