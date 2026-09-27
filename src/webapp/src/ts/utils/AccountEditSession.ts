import { ApiEndpoints } from "../auto/ApiEndpoints";
import { AccountBasicConfigDto, LearningShortcutConfiguration, MailFilterRuleConfiguration } from "../auto/pieroxy-mom";

/** One rule in a session's working list, tracking enough to render "Moved"/"Deleted"/"New" tags and to rebuild the final rules array on save. */
export interface PendingRule {
  rule: MailFilterRuleConfiguration;
  /** Position in the rules the account settings page originally loaded, or null if this rule was added during this session. */
  originalIndex: number | null;
  deleted: boolean;
  edited: boolean;
}

/**
 * One account's whole set of in-flight, unsaved edits — Config, Credentials and Rules — staged
 * locally while the user reviews them, and flushed to the backend in a single call only when they
 * click "Save Changes" (see UpdateAccountApi). Nothing here is persisted until then; Cancel/
 * Discard just throws the session away.
 */
export interface AccountEditSession {
  accountName: string;
  baselineConfig: AccountBasicConfigDto;
  workingConfig: AccountBasicConfigDto;
  credentialsKey: string;
  baselineUsername: string;
  workingUsername: string;
  /** Empty means "no new password staged" — the same "blank = unchanged" convention the backend uses. */
  workingPassword: string;
  rules: PendingRule[];
  shortcuts: LearningShortcutConfiguration[];
}

function isDeepEqual(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

export function isConfigFieldChanged<K extends keyof AccountBasicConfigDto>(session: AccountEditSession, field: K): boolean {
  return !isDeepEqual(session.baselineConfig[field], session.workingConfig[field]);
}

export function isConfigChanged(session: AccountEditSession): boolean {
  return !isDeepEqual(session.baselineConfig, session.workingConfig);
}

export function isCredentialsChanged(session: AccountEditSession): boolean {
  return session.workingUsername !== session.baselineUsername || session.workingPassword !== "";
}

export function isRuleMoved(session: AccountEditSession, pendingRule: PendingRule): boolean {
  return pendingRule.originalIndex !== null && pendingRule.originalIndex !== session.rules.indexOf(pendingRule);
}

export function isRulesChanged(session: AccountEditSession): boolean {
  return session.rules.some((r) => r.deleted || r.edited || r.originalIndex === null || isRuleMoved(session, r));
}

export function isSessionChanged(session: AccountEditSession): boolean {
  return isConfigChanged(session) || isCredentialsChanged(session) || isRulesChanged(session);
}

class AccountEditSessionStore {
  private sessions = new Map<string, AccountEditSession>();

  /**
   * Hands back the account's in-flight session, starting a fresh one from the backend's current
   * state if there isn't one yet — e.g. the settings page's first visit, or right after Save/
   * Discard. Once a session exists it's reused as-is: navigating to an edit sub-page and back
   * must never silently refetch and drop pending edits.
   */
  load(accountName: string): Promise<AccountEditSession> {
    const existing = this.sessions.get(accountName);
    if (existing) return Promise.resolve(existing);

    return ApiEndpoints.AccountConfig.call({ accountName }).then((output) => {
      const session: AccountEditSession = {
        accountName,
        baselineConfig: output.config,
        workingConfig: { ...output.config },
        credentialsKey: output.credentials.credentialsKey,
        baselineUsername: output.credentials.username,
        workingUsername: output.credentials.username,
        workingPassword: "",
        rules: output.rules.map((rule, index) => ({ rule, originalIndex: index, deleted: false, edited: false })),
        shortcuts: output.shortcuts,
      };
      this.sessions.set(accountName, session);
      return session;
    });
  }

  discard(accountName: string) {
    this.sessions.delete(accountName);
  }
}

export const accountEditSessions = new AccountEditSessionStore();
