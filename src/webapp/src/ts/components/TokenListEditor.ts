import m from "mithril";

interface TokenListEditorAttrs {
  tokens: string[];
  onChange: (tokens: string[]) => void;
  placeholder?: string;
  disabled?: boolean;
  /** When given, tokens must come from this fixed set (e.g. an enum's values) — the free-text input is replaced by a dropdown offering only the values not already added. */
  options?: string[];
}

/** Above this many tokens, a filter box narrows what's displayed — see the class doc. */
const FILTER_THRESHOLD = 20;

/**
 * A list of removable pill tokens plus a way to add more — a free-text input, or, when `options`
 * is given, a dropdown restricted to that fixed set. Used anywhere a config field is a small set
 * of strings (excluded folders, matcher keys, reputation list ids, ...) — including, potentially,
 * a learned rule's accumulated keys, which can run into the hundreds or thousands (e.g. a
 * long-lived "move to spam" shortcut). Past FILTER_THRESHOLD tokens, a search box appears above
 * the pill list so finding one specific value to remove doesn't mean scrolling through all of
 * them — it narrows the *display* only, never the underlying array `onChange` operates on.
 */
export class TokenListEditor implements m.ClassComponent<TokenListEditorAttrs> {
  private newToken = "";
  private filter = "";

  view({ attrs }: m.Vnode<TokenListEditorAttrs>): m.Children {
    const { tokens, onChange, placeholder, disabled, options } = attrs;
    const showFilter = tokens.length > FILTER_THRESHOLD;
    const trimmedFilter = this.filter.trim().toLowerCase();
    const visibleTokens = showFilter && trimmedFilter
      ? tokens.filter((t) => t.toLowerCase().includes(trimmedFilter))
      : tokens;

    return m(".token-editor", [
      showFilter ? m("input.token-filter", {
        type: "text", value: this.filter, placeholder: "Filter " + tokens.length + " values…",
        oninput: (e: Event) => (this.filter = (e.target as HTMLInputElement).value),
      }) : null,
      showFilter && visibleTokens.length !== tokens.length
        ? m(".token-filter-count", visibleTokens.length + " of " + tokens.length + " shown")
        : null,
      m(".token-list", visibleTokens.map((token) => m(".token", { key: token }, [
        token,
        m("span.token-remove" + (disabled ? ".disabled" : ""), { onclick: () => onChange(tokens.filter((t) => t !== token)) }, "×"),
      ]))),
      options ? this.renderOptionAdd(tokens, options, onChange, disabled) : this.renderFreeTextAdd(tokens, onChange, placeholder, disabled),
    ]);
  }

  private renderFreeTextAdd(tokens: string[], onChange: (tokens: string[]) => void, placeholder?: string, disabled?: boolean): m.Children {
    return m(".token-editor-add", [
      m("input", {
        type: "text", value: this.newToken, placeholder: placeholder || "Add…", disabled,
        oninput: (e: Event) => (this.newToken = (e.target as HTMLInputElement).value),
        onkeydown: (e: KeyboardEvent) => {
          if (e.key === "Enter") {
            e.preventDefault();
            this.addFreeText(tokens, onChange);
          }
        },
      }),
      m("button", { onclick: () => this.addFreeText(tokens, onChange), disabled }, "Add"),
    ]);
  }

  private renderOptionAdd(tokens: string[], options: string[], onChange: (tokens: string[]) => void, disabled?: boolean): m.Children {
    const remaining = options.filter((o) => !tokens.includes(o));
    if (remaining.length === 0) return null;
    const selected = remaining.includes(this.newToken) ? this.newToken : remaining[0];
    return m(".token-editor-add", [
      m("select", {
        value: selected, disabled,
        onchange: (e: Event) => (this.newToken = (e.target as HTMLSelectElement).value),
      }, remaining.map((o) => m("option", { value: o }, o))),
      m("button", { onclick: () => onChange([...tokens, selected]), disabled }, "Add"),
    ]);
  }

  private addFreeText(tokens: string[], onChange: (tokens: string[]) => void) {
    const value = this.newToken.trim();
    if (value && !tokens.includes(value)) {
      onChange([...tokens, value]);
    }
    this.newToken = "";
  }
}
