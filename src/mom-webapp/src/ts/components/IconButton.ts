import m from "mithril";

interface IconButtonAttrs {
  icon: m.ComponentTypes;
  title: string;
  onclick: () => void;
  /** Icon size: small 18px (rows), medium 22px (default), large 26px (page header), xlarge 40px (toolbar). */
  size?: "small" | "medium" | "large" | "xlarge";
  /** Tiny label under the icon. */
  label?: string;
  /** The page this icon leads to is the current one: highlighted, not clickable. */
  selected?: boolean;
  disabled?: boolean;
  /** An action in progress: the icon spins and clicks are ignored. */
  busy?: boolean;
  /** A destructive action: red on hover, and red for good while {@link active}. */
  danger?: boolean;
  active?: boolean;
  /** Dark icon, for use on an accent-colored background. */
  onAccent?: boolean;
  /** Extra classes, for the caller's own layout. */
  class?: string;
}

/** Every clickable icon in the app: rest, hover, selected, disabled and busy states. */
export class IconButton implements m.ClassComponent<IconButtonAttrs> {
  view({ attrs }: m.Vnode<IconButtonAttrs>): m.Children {
    const inert = attrs.selected || attrs.disabled || attrs.busy;
    const modifiers = ["size-" + (attrs.size ?? "medium")];
    if (attrs.selected) modifiers.push("selected");
    if (attrs.disabled) modifiers.push("disabled");
    if (attrs.busy) modifiers.push("busy");
    if (attrs.danger) modifiers.push("danger");
    if (attrs.active) modifiers.push("active");
    if (attrs.onAccent) modifiers.push("on-accent");
    if (attrs.class) modifiers.push(attrs.class);
    return m("span.icon-button", {
      class: modifiers.join(" "),
      title: attrs.title,
      "aria-current": attrs.selected ? "page" : undefined,
      onclick: () => {
        if (!inert) attrs.onclick();
      },
    }, [
      m(attrs.icon),
      attrs.label ? m(".icon-button-label", attrs.label) : null,
    ]);
  }
}
