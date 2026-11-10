import m from "mithril";
import { TokenListEditor } from "./TokenListEditor";

const EMAIL = /^[^@\s]+@[^@\s]+$/;
const DOMAIN = /^@[^@\s]+\.[^@\s]+$/;

/** Mirrors AccountAddresses#normalize. */
export function validateAddresses(addresses: string[]): string | undefined {
  const invalid = addresses.find((address) => !EMAIL.test(address.trim()) && !DOMAIN.test(address.trim()));
  return invalid !== undefined ? "\"" + invalid + "\" is neither an email address nor a domain (@example.com)." : undefined;
}

/** Trimmed, lower-cased and deduplicated, as the server stores them. */
export function normalizeAddresses(addresses: string[]): string[] {
  return [...new Set(addresses.map((address) => address.trim().toLowerCase()).filter((address) => address))];
}

interface AccountAddressesFormAttrs {
  addresses: string[];
  onChange: (addresses: string[]) => void;
  /** An "originally: …" hint, or null when unchanged. */
  originalHint?: string | null;
}

/** The account's addresses field, shared by AccountAddressesEditPage and the account creation wizard. */
export class AccountAddressesForm implements m.ClassComponent<AccountAddressesFormAttrs> {
  view({ attrs }: m.Vnode<AccountAddressesFormAttrs>): m.Children {
    return m(".edit-field", [
      m("label", "Addresses"),
      m(TokenListEditor, {
        tokens: attrs.addresses,
        onChange: attrs.onChange,
        placeholder: "me@example.com or @example.com",
      }),
      m("span.field-hint", "The addresses delivered to this mailbox, @example.com standing for every address of a domain. "
        + "Automatic replies only answer mail sent to one of them, or to the SMTP sender address."),
      attrs.originalHint ? m("span.field-original", attrs.originalHint) : null,
    ]);
  }
}
