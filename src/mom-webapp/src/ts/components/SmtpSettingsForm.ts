import m from "mithril";
import { SmtpSecurity, SmtpSettingsDto } from "../auto/pieroxy-mom";
import { TokenListEditor } from "./TokenListEditor";

const DEFAULT_PORTS: Record<SmtpSecurity, number> = { [SmtpSecurity.TLS]: 465, [SmtpSecurity.STARTTLS]: 587 };
export const SECURITY_LABELS: Record<SmtpSecurity, string> = {
  [SmtpSecurity.TLS]: "TLS (usually port 465)",
  [SmtpSecurity.STARTTLS]: "STARTTLS (usually port 587)",
};
const EMAIL = /^[^@\s]+@[^@\s]+$/;
const DOMAIN_ALIAS = /^@[^@\s]+\.[^@\s]+$/;

export function defaultSmtp(imapUsername: string): SmtpSettingsDto {
  return {
    host: "",
    port: DEFAULT_PORTS[SmtpSecurity.STARTTLS],
    security: SmtpSecurity.STARTTLS,
    fromAddress: imapUsername.includes("@") ? imapUsername : "",
    aliases: [],
    ownCredentials: false,
    username: "",
  };
}

/** Mirrors SmtpSettingsDto#toConfiguration. {@code passwordMissing}: own credentials without any password, typed or saved. */
export function validateSmtp(smtp: SmtpSettingsDto, passwordMissing: boolean): string | undefined {
  if (!smtp.host.trim()) return "Host must not be blank.";
  if (smtp.port <= 0 || smtp.port > 65535) return "Port must be between 1 and 65535.";
  if (!EMAIL.test(smtp.fromAddress.trim())) return "The sender address must be a valid email address.";
  const invalidAlias = smtp.aliases.find((alias) => !EMAIL.test(alias.trim()) && !DOMAIN_ALIAS.test(alias.trim()));
  if (invalidAlias !== undefined) return "\"" + invalidAlias + "\" is neither an email address nor a domain (@example.com).";
  if (smtp.ownCredentials) {
    if (!smtp.username.trim()) return "SMTP username must not be blank.";
    if (passwordMissing) return "The SMTP password must not be blank.";
  }
  return undefined;
}

/** Trimmed, aliases lower-cased and deduplicated as the server stores them, and no username without own credentials. */
export function finalizeSmtp(smtp: SmtpSettingsDto): SmtpSettingsDto {
  return {
    ...smtp,
    host: smtp.host.trim(),
    fromAddress: smtp.fromAddress.trim(),
    aliases: [...new Set(smtp.aliases.map((alias) => alias.trim().toLowerCase()).filter((alias) => alias))],
    username: smtp.ownCredentials ? smtp.username : "",
  };
}

interface SmtpSettingsFormAttrs {
  /** Edited in place. */
  smtp: SmtpSettingsDto;
  password: string;
  onPasswordChange: (password: string) => void;
  /** An "originally: …" hint for a field, or null when unchanged. Absent: no hints. */
  originalHint?: (field: keyof SmtpSettingsDto) => string | null;
  passwordPlaceholder?: string;
  /** Shown under the password field. */
  passwordHints?: m.Children;
}

/** The SMTP settings fields, shared by AccountSmtpSettingsEditPage and the account creation wizard. */
export class SmtpSettingsForm implements m.ClassComponent<SmtpSettingsFormAttrs> {
  view({ attrs }: m.Vnode<SmtpSettingsFormAttrs>): m.Children {
    const { smtp } = attrs;
    const hint = (field: keyof SmtpSettingsDto) => attrs.originalHint ? attrs.originalHint(field) : null;
    return [
      field("Host", hint("host"), m("input", {
        type: "text", value: smtp.host,
        oninput: (e: Event) => (smtp.host = (e.target as HTMLInputElement).value),
      })),
      field("Security", hint("security"), m("select", {
        value: smtp.security,
        onchange: (e: Event) => changeSecurity(smtp, (e.target as HTMLSelectElement).value as SmtpSecurity),
      }, Object.values(SmtpSecurity).map((security) => m("option", { value: security }, SECURITY_LABELS[security])))),
      field("Port", hint("port"), m("input", {
        type: "number", value: smtp.port,
        oninput: (e: Event) => (smtp.port = Number((e.target as HTMLInputElement).value)),
      })),
      field("Sender address", hint("fromAddress"), m("input", {
        type: "email", value: smtp.fromAddress,
        oninput: (e: Event) => (smtp.fromAddress = (e.target as HTMLInputElement).value),
      }), "The From address of the mail MOM sends for this account."),
      field("Aliases", hint("aliases"), m(TokenListEditor, {
        tokens: smtp.aliases,
        onChange: (aliases) => (smtp.aliases = aliases),
        placeholder: "alias@example.com or @example.com",
      }), "Other addresses delivered to this mailbox. Automatic replies only answer mail sent to the sender address or one of these."),
      field("Login", hint("ownCredentials"), m("label.checkbox-field", [
        m("input", {
          type: "checkbox", checked: !smtp.ownCredentials,
          onchange: (e: Event) => (smtp.ownCredentials = !(e.target as HTMLInputElement).checked),
        }),
        "Same as the IMAP login",
      ])),
      smtp.ownCredentials ? [
        field("SMTP username", hint("username"), m("input", {
          type: "text", value: smtp.username, autocomplete: "off",
          oninput: (e: Event) => (smtp.username = (e.target as HTMLInputElement).value),
        })),
        m(".edit-field", [
          m("label", "SMTP password"),
          m("input", {
            type: "password", autocomplete: "new-password", value: attrs.password, placeholder: attrs.passwordPlaceholder ?? "",
            oninput: (e: Event) => attrs.onPasswordChange((e.target as HTMLInputElement).value),
          }),
          attrs.passwordHints,
        ]),
      ] : null,
    ];
  }
}

/** Follows the security mode's usual port, unless a non-standard one was typed. */
function changeSecurity(smtp: SmtpSettingsDto, security: SmtpSecurity) {
  if (Object.values(DEFAULT_PORTS).includes(smtp.port) || !smtp.port) smtp.port = DEFAULT_PORTS[security];
  smtp.security = security;
}

function field(label: string, originalHint: string | null, control: m.Children, fieldHint?: string): m.Children {
  return m(".edit-field", [
    m("label", label),
    control,
    fieldHint ? m("span.field-hint", fieldHint) : null,
    originalHint ? m("span.field-original", originalHint) : null,
  ]);
}
