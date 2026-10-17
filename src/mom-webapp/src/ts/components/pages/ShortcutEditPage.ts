import m from "mithril";
import {
  ActionType,
  LearningShortcutConfiguration,
  MailFilterRuleActionConfiguration,
  MailFilterRuleMatcherConfiguration,
  MatcherType,
} from "../../auto/pieroxy-mom";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { AbstractPage } from "./AbstractPage";
import { Routing } from "../../utils/navigation/Routing";
import { AccountEditSession, PendingShortcut, accountEditSessions } from "../../utils/AccountEditSession";

interface ShortcutEditPageAttrs {
  accountName: string;
  /** "new" to append a shortcut, or its position in the session's shortcuts list (as a string, straight from the route) to edit it. */
  shortcutIndex: string;
}

interface ShortcutMatcherTypeOption {
  value: MatcherType;
  label: string;
}

// A shortcut's matcher only ever carries a type — its key is extracted from each example dropped
// into the folder, same as the discovery tree (see LearningShortcutConfiguration/RuleLearner) —
// so only the four "learnable" leaf matcher types are offered, and there's no value field at all.
const SHORTCUT_MATCHER_TYPE_OPTIONS: ShortcutMatcherTypeOption[] = [
  { value: MatcherType.FROM_EQUALS, label: "From (full header) equals" },
  { value: MatcherType.FROM_ADDRESS_EQUALS, label: "From address equals" },
  { value: MatcherType.FROM_DOMAIN_EQUALS, label: "From domain equals" },
  { value: MatcherType.SUBJECT_STARTS_WITH, label: "Subject starts with" },
];

interface ShortcutActionTypeOption {
  value: ActionType;
  label: string;
  keyLabel: string;
  isFolderName: boolean;
}

// Same reasoning: only the "learnable" leaf action types (see ActionType.learnableValues()).
const SHORTCUT_ACTION_TYPE_OPTIONS: ShortcutActionTypeOption[] = [
  { value: ActionType.MOVE_TO, label: "Move to folder", keyLabel: "Destination folder", isFolderName: true },
  { value: ActionType.MOVE_TO_AND_READ, label: "Move to folder and mark as read", keyLabel: "Destination folder", isFolderName: true },
  { value: ActionType.NOOP, label: "Do nothing (stop processing, log only)", keyLabel: "Log message", isFolderName: false },
];

// Mirrors RuleLearner.isReservedName: "Done" plus every learnable matcher type's own name, since
// those are the discovery tree's top-level folder names a shortcut folder must not collide with.
const RESERVED_SHORTCUT_NAMES = new Set<string>(["Done", ...SHORTCUT_MATCHER_TYPE_OPTIONS.map((o) => o.value as string)]);

const FOLDER_DATALIST_ID = "shortcut-destination-folder-options";

/**
 * Creates or edits one learning shortcut — a single mom-rules/<name> folder bound to one fixed
 * (matcher type, action) pair (see LearningShortcutConfiguration/RuleLearner), for the handful of
 * matcher/action combinations actually used day to day without needing an IMAP client subscribed
 * to the full discovery tree. Nothing is sent to the backend here: "OK" just writes the edited
 * shortcut into the account's AccountEditSession and returns to the settings page, same pattern as
 * RuleEditPage.
 */
export class ShortcutEditPage extends AbstractPage<ShortcutEditPageAttrs> {
  private accountName = "";
  private shortcutIndex: number | null = null; // null = creating a new shortcut
  private session: AccountEditSession | undefined;

  private name = "";
  private matcherType: MatcherType = MatcherType.FROM_DOMAIN_EQUALS;
  private actionType: ActionType = ActionType.MOVE_TO;
  private actionKey = "";
  private accountFolders: string[] = [];

  private loading = true;
  private error: string | undefined;

  getPageTitle(): string {
    return "MOM - Edit shortcut";
  }

  oninit({ attrs }: m.Vnode<ShortcutEditPageAttrs>) {
    this.accountName = attrs.accountName;
    this.shortcutIndex = attrs.shortcutIndex === "new" ? null : Number(attrs.shortcutIndex);
    this.load();
  }

  render(): m.Children {
    const title = (this.shortcutIndex === null ? "New shortcut — " : "Edit shortcut — ") + this.accountName;
    return m("page.shortcuteditpage", [
      m(".page-header", [
        m("a.page-back", { onclick: () => this.cancel() }, "‹ Back to settings"),
        m("h1.page-title", title),
      ]),
      this.error ? m(".settings-error.errorMessage", this.error) : null,
      this.loading ? m(".page-loading", "Loading…") : this.renderForm(),
    ]);
  }

  private renderForm(): m.Children {
    return m(".page-card.edit-form", [
      this.field("Name", m("input", {
        type: "text", value: this.name, placeholder: "MoveNewsletterToArchive",
        oninput: (e: Event) => (this.name = (e.target as HTMLInputElement).value),
      })),
      this.field("Matcher", m("select", {
        value: this.matcherType,
        onchange: (e: Event) => (this.matcherType = (e.target as HTMLSelectElement).value as MatcherType),
      }, SHORTCUT_MATCHER_TYPE_OPTIONS.map((o) => m("option", { value: o.value }, o.label)))),
      this.field("Action", m("select", {
        value: this.actionType,
        onchange: (e: Event) => (this.actionType = (e.target as HTMLSelectElement).value as ActionType),
      }, SHORTCUT_ACTION_TYPE_OPTIONS.map((o) => m("option", { value: o.value }, o.label)))),
      this.field(this.actionOption().keyLabel, this.actionOption().isFolderName ? this.renderFolderField() : this.renderTextKeyField()),
      m(".edit-actions", [
        m("button.ok-button", { onclick: () => this.apply() }, "OK"),
        m("button.cancel-button", { onclick: () => this.cancel() }, "Cancel"),
      ]),
    ]);
  }

  /** Same editable dropdown as RuleEditPage's destination folder: pick an existing folder or type one that doesn't exist yet. */
  private renderFolderField(): m.Children {
    return [
      m("input", {
        type: "text", value: this.actionKey, list: FOLDER_DATALIST_ID, placeholder: "Folder name",
        oninput: (e: Event) => (this.actionKey = (e.target as HTMLInputElement).value),
      }),
      m("datalist#" + FOLDER_DATALIST_ID, this.accountFolders.map((folder) => m("option", { key: folder, value: folder }))),
    ];
  }

  private renderTextKeyField(): m.Children {
    return m("input", {
      type: "text", value: this.actionKey, placeholder: "What to log when a learned rule matches",
      oninput: (e: Event) => (this.actionKey = (e.target as HTMLInputElement).value),
    });
  }

  private actionOption(): ShortcutActionTypeOption {
    return SHORTCUT_ACTION_TYPE_OPTIONS.find((o) => o.value === this.actionType) || SHORTCUT_ACTION_TYPE_OPTIONS[0];
  }

  private field(label: string, control: m.Children): m.Children {
    return m(".edit-field", [m("label", label), control]);
  }

  private cancel() {
    Routing.goToAccountSettings(this.accountName);
  }

  private load() {
    this.loading = true;
    this.error = undefined;
    Promise.all([
      accountEditSessions.load(this.accountName),
      ApiEndpoints.AccountFolders.call({ accountName: this.accountName }).catch(() => ({ folders: [] as string[] })),
    ])
      .then(([session, accountFolders]) => {
        this.session = session;
        this.accountFolders = accountFolders.folders;
        if (this.shortcutIndex !== null) {
          const pendingShortcut = session.shortcuts[this.shortcutIndex];
          if (!pendingShortcut) {
            this.error = "No such shortcut.";
            this.loading = false;
            m.redraw();
            return;
          }
          this.applyShortcut(pendingShortcut.shortcut);
        }
        this.loading = false;
        m.redraw();
      })
      .catch((err: Error) => {
        this.loading = false;
        this.error = err.message;
        m.redraw();
      });
  }

  private applyShortcut(shortcut: LearningShortcutConfiguration) {
    this.name = shortcut.name;
    this.matcherType = shortcut.matcher.type;
    this.actionType = shortcut.action.type;
    this.actionKey = shortcut.action.key || "";
  }

  /** Mirrors RuleLearner.validateShortcuts — catches the common mistakes before they're staged into the session instead of only surfacing them at "Save Changes" time. */
  private validate(): string | undefined {
    const trimmedName = this.name.trim();
    if (!trimmedName) return "The shortcut needs a name.";
    if (RESERVED_SHORTCUT_NAMES.has(trimmedName)) return "\"" + trimmedName + "\" is a reserved name — pick another.";

    const session = this.session!;
    const collides = session.shortcuts.some((pending, index) =>
      index !== this.shortcutIndex && !pending.deleted && pending.shortcut.name === trimmedName);
    if (collides) return "A shortcut named \"" + trimmedName + "\" already exists.";

    if (!this.actionKey.trim()) return "The action needs a " + this.actionOption().keyLabel.toLowerCase() + ".";
    return undefined;
  }

  private apply() {
    const validationError = this.validate();
    if (validationError) {
      this.error = validationError;
      return;
    }

    const matcher: MailFilterRuleMatcherConfiguration = { type: this.matcherType } as MailFilterRuleMatcherConfiguration;
    const action: MailFilterRuleActionConfiguration = { type: this.actionType, key: this.actionKey.trim() } as MailFilterRuleActionConfiguration;
    const shortcut: LearningShortcutConfiguration = { name: this.name.trim(), matcher, action };

    const session = this.session!;
    if (this.shortcutIndex === null) {
      const newPendingShortcut: PendingShortcut = { shortcut, originalIndex: null, deleted: false, edited: false };
      session.shortcuts = [...session.shortcuts, newPendingShortcut];
    } else {
      const previous = session.shortcuts[this.shortcutIndex];
      const updated: PendingShortcut = { ...previous, shortcut, edited: true };
      session.shortcuts = session.shortcuts.map((pending, index) => (index === this.shortcutIndex ? updated : pending));
    }

    Routing.goToAccountSettings(this.accountName);
  }
}
