import m from "mithril";

interface TokenListEditorAttrs {
  tokens: string[];
  onChange: (tokens: string[]) => void;
  placeholder?: string;
  disabled?: boolean;
}

/** A list of removable pill tokens plus a text input to add more — used anywhere a config field is a small set of strings (excluded folders, matcher keys, reputation list ids, ...). */
export class TokenListEditor implements m.ClassComponent<TokenListEditorAttrs> {
  private newToken = "";

  view({ attrs }: m.Vnode<TokenListEditorAttrs>): m.Children {
    const { tokens, onChange, placeholder, disabled } = attrs;
    return m(".token-editor", [
      m(".token-list", tokens.map((token) => m(".token", { key: token }, [
        token,
        m("span.token-remove", { onclick: () => onChange(tokens.filter((t) => t !== token)) }, "×"),
      ]))),
      m(".token-editor-add", [
        m("input", {
          type: "text", value: this.newToken, placeholder: placeholder || "Add…", disabled,
          oninput: (e: Event) => (this.newToken = (e.target as HTMLInputElement).value),
          onkeydown: (e: KeyboardEvent) => {
            if (e.key === "Enter") {
              e.preventDefault();
              this.add(tokens, onChange);
            }
          },
        }),
        m("button", { onclick: () => this.add(tokens, onChange), disabled }, "Add"),
      ]),
    ]);
  }

  private add(tokens: string[], onChange: (tokens: string[]) => void) {
    const value = this.newToken.trim();
    if (value && !tokens.includes(value)) {
      onChange([...tokens, value]);
    }
    this.newToken = "";
  }
}
