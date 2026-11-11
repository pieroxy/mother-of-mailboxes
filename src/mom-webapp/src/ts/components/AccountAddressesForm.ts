import m from "mithril";
import { TokenListEditor } from "./TokenListEditor";

const EMAIL = /^[^@\s]+@[^@\s]+$/;
const DOMAIN = /^@[^@\s]+\.[^@\s]+$/;
const HEADER_NAME = /^[!-9;-~]+$/;

/** Where Dovecot (Delivered-To), Postfix (X-Original-To, Delivered-To) and Exim (Envelope-To) record the delivery address. */
export const RECOMMENDED_ENVELOPE_HEADERS = ["Delivered-To", "X-Original-To", "Envelope-To"];

/** Mirrors AccountAddresses#normalize. */
export function validateAddresses(addresses: string[]): string | undefined {
  const invalid = addresses.find((address) => !EMAIL.test(address.trim()) && !DOMAIN.test(address.trim()));
  return invalid !== undefined ? "\"" + invalid + "\" is neither an email address nor a domain (@example.com)." : undefined;
}

/** Trimmed, lower-cased and deduplicated, as the server stores them. */
export function normalizeAddresses(addresses: string[]): string[] {
  return [...new Set(addresses.map((address) => address.trim().toLowerCase()).filter((address) => address))];
}

/** Mirrors EnvelopeHeaders#normalize. */
export function validateEnvelopeHeaders(headers: string[]): string | undefined {
  const invalid = headers.find((header) => !HEADER_NAME.test(header.trim()));
  return invalid !== undefined ? "\"" + invalid + "\" is not a valid header name." : undefined;
}

/** Trimmed and deduplicated ignoring case, as the server stores them. */
export function normalizeEnvelopeHeaders(headers: string[]): string[] {
  const seen = new Set<string>();
  return headers.map((header) => header.trim()).filter((header) => {
    if (!header || seen.has(header.toLowerCase())) return false;
    seen.add(header.toLowerCase());
    return true;
  });
}

export function isRecommendedEnvelopeHeaders(headers: string[]): boolean {
  const lower = new Set(headers.map((header) => header.toLowerCase()));
  return lower.size === RECOMMENDED_ENVELOPE_HEADERS.length && RECOMMENDED_ENVELOPE_HEADERS.every((header) => lower.has(header.toLowerCase()));
}

/** For read-only displays. */
export function envelopeHeadersLabel(headers: string[]): string {
  if (headers.length === 0) return "None";
  return isRecommendedEnvelopeHeaders(headers) ? "Recommended (" + headers.join(", ") + ")" : headers.join(", ");
}

type EnvelopeHeadersMode = "none" | "recommended" | "custom";

interface AccountAddressesFormAttrs {
  addresses: string[];
  onAddressesChange: (addresses: string[]) => void;
  envelopeHeaders: string[];
  onEnvelopeHeadersChange: (headers: string[]) => void;
  /** "originally: …" hints, or null when unchanged. */
  addressesOriginalHint?: string | null;
  envelopeHeadersOriginalHint?: string | null;
}

/** The account's addresses and envelope headers, shared by AccountAddressesEditPage and the account creation wizard. */
export class AccountAddressesForm implements m.ClassComponent<AccountAddressesFormAttrs> {
  // Kept apart from the list: an empty custom list is still "custom".
  private mode: EnvelopeHeadersMode = "none";

  oninit({ attrs }: m.Vnode<AccountAddressesFormAttrs>) {
    if (attrs.envelopeHeaders.length === 0) this.mode = "none";
    else this.mode = isRecommendedEnvelopeHeaders(attrs.envelopeHeaders) ? "recommended" : "custom";
  }

  view({ attrs }: m.Vnode<AccountAddressesFormAttrs>): m.Children {
    return [
      m(".edit-field", [
        m("label", "Addresses"),
        m(TokenListEditor, {
          tokens: attrs.addresses,
          onChange: attrs.onAddressesChange,
          placeholder: "me@example.com or @example.com",
        }),
        m("span.field-hint", "The addresses delivered to this mailbox, @example.com standing for every address of a domain. "
          + "Automatic replies only answer mail sent to one of them, or to the SMTP sender address. "
          + "Rules learned on a recipient learn the one of them the example was sent to."),
        attrs.addressesOriginalHint ? m("span.field-original", attrs.addressesOriginalHint) : null,
      ]),
      m(".edit-field", [
        m("label", "Envelope headers"),
        m(".radio-group", [
          this.radio(attrs, "none", "None: recipients are only read from To and Cc"),
          this.radio(attrs, "recommended", "Recommended: " + RECOMMENDED_ENVELOPE_HEADERS.join(", ")),
          this.radio(attrs, "custom", "Custom"),
        ]),
        this.mode === "custom" ? m(TokenListEditor, {
          tokens: attrs.envelopeHeaders,
          onChange: attrs.onEnvelopeHeadersChange,
          placeholder: "Header name",
        }) : null,
        m("span.field-hint", "Headers in which your mail server records the address a message was delivered to: "
          + "the only place it appears for mailing lists, Bcc and catch-all mail. Only the topmost occurrence is read. "
          + "Leave out a header your server doesn't add: a sender could forge it."),
        attrs.envelopeHeadersOriginalHint ? m("span.field-original", attrs.envelopeHeadersOriginalHint) : null,
      ]),
    ];
  }

  private radio(attrs: AccountAddressesFormAttrs, mode: EnvelopeHeadersMode, label: string): m.Children {
    return m("label.checkbox-field", [
      m("input", { type: "radio", name: "envelope-headers", checked: this.mode === mode, onchange: () => this.changeMode(attrs, mode) }),
      label,
    ]);
  }

  /** "Custom" starts from the current list. */
  private changeMode(attrs: AccountAddressesFormAttrs, mode: EnvelopeHeadersMode) {
    this.mode = mode;
    if (mode === "none") attrs.onEnvelopeHeadersChange([]);
    else if (mode === "recommended") attrs.onEnvelopeHeadersChange([...RECOMMENDED_ENVELOPE_HEADERS]);
  }
}
