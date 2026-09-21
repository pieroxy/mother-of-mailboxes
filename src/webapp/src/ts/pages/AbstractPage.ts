import m from "mithril";

/**
 * Minimal page base: just the browser tab title and a render hook. Deliberately doesn't bring in
 * shared chrome (nav bar, notifications, dialogs) yet — there's only two pages so far; that's
 * worth adding once there's enough of them to actually need navigating between.
 */
export abstract class AbstractPage<A = {}> implements m.ClassComponent<A> {
  abstract getPageTitle(): string;
  abstract render(vnode: m.Vnode<A, this>): m.Children;

  view(vnode: m.Vnode<A, this>): m.Children {
    window.document.title = this.getPageTitle();
    return this.render(vnode);
  }
}
