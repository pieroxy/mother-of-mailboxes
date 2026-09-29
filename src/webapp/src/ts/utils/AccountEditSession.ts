import { ApiEndpoints } from "../auto/ApiEndpoints";
import { AccountBasicConfigDto, LearningShortcutConfiguration, MailFilterRuleConfiguration, MailFilterRuleMatcherConfiguration } from "../auto/pieroxy-mom";

/** One rule in a session's working list, tracking enough to render "Moved"/"Deleted"/"New" tags and to rebuild the final rules array on save. */
export interface PendingRule {
  rule: MailFilterRuleConfiguration;
  /** Position in the rules the account settings page originally loaded, or null if this rule was added during this session. */
  originalIndex: number | null;
  deleted: boolean;
  edited: boolean;
}

/** Same idea as PendingRule, for the Shortcuts list — no "moved" concept, since shortcuts are an unordered set of folders, not a priority list. */
export interface PendingShortcut {
  shortcut: LearningShortcutConfiguration;
  originalIndex: number | null;
  deleted: boolean;
  edited: boolean;
}

/**
 * One entry of the account's learned-by-example rules (see LearnedRulesApi) — the settings page
 * lets its key list be reviewed and corrected (e.g. a message dropped in the wrong mom-rules/
 * folder by mistake). No "new"/"moved" concept: entries are only ever learned by example or
 * deleted here, never hand-created, and their order is meaningless (unlike Rules).
 */
export interface PendingLearnedRule {
  rule: MailFilterRuleConfiguration;
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
  shortcuts: PendingShortcut[];
  learnedRules: PendingLearnedRule[];
  /**
   * Whether the account currently runs. Deliberately outside the staged/diffed fields above:
   * pause/resume (see SetAccountActiveApi) takes effect immediately from the settings page, not
   * staged and flushed together with "Save Changes" — so the page updates this in place on success
   * rather than going through the rest of this session's change tracking.
   */
  active: boolean;
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

export function isShortcutsChanged(session: AccountEditSession): boolean {
  return session.shortcuts.some((s) => s.deleted || s.edited || s.originalIndex === null);
}

export function isLearnedRulesChanged(session: AccountEditSession): boolean {
  return session.learnedRules.some((r) => r.deleted || r.edited);
}

export function isSessionChanged(session: AccountEditSession): boolean {
  return isConfigChanged(session) || isCredentialsChanged(session) || isRulesChanged(session)
    || isShortcutsChanged(session) || isLearnedRulesChanged(session);
}

/**
 * Normalizes a learned rule's matcher right before it's saved: collapses a single-element "keys"
 * array down to "key", matching the shape LearnedRulesStore itself uses for a scalar match (and
 * keeping the hand-editable JSON file it owns exactly as readable as it already is). Editing
 * itself always works with the array form — see AccountSettingsPage's TokenListEditor binding.
 */
export function finalizeLearnedRule(rule: MailFilterRuleConfiguration): MailFilterRuleConfiguration {
  const keys = rule.matcher.keys && rule.matcher.keys.length > 0 ? rule.matcher.keys
    : (rule.matcher.key ? [rule.matcher.key] : []);
  const matcher = keys.length === 1
    ? ({ type: rule.matcher.type, key: keys[0] } as MailFilterRuleMatcherConfiguration)
    : ({ type: rule.matcher.type, keys } as MailFilterRuleMatcherConfiguration);
  return { ...rule, matcher };
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

    return Promise.all([
      ApiEndpoints.AccountConfig.call({ accountName }),
      ApiEndpoints.LearnedRules.call({ accountName }),
    ]).then(([output, learnedRulesOutput]) => {
      const session: AccountEditSession = {
        accountName,
        baselineConfig: output.config,
        workingConfig: { ...output.config },
        credentialsKey: output.credentials.credentialsKey,
        baselineUsername: output.credentials.username,
        workingUsername: output.credentials.username,
        workingPassword: "",
        rules: output.rules.map((rule, index) => ({ rule, originalIndex: index, deleted: false, edited: false })),
        shortcuts: output.shortcuts.map((shortcut, index) => ({ shortcut, originalIndex: index, deleted: false, edited: false })),
        learnedRules: learnedRulesOutput.rules.map((rule) => ({ rule, deleted: false, edited: false })),
        active: output.active,
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
