import m from "mithril";
import { Toolbar } from "../Toolbar";
import { Dialogs } from "../../utils/Dialogs";

/** Page base: sets the browser tab title and wraps render() with the shared chrome (dialog, toolbar). Notifications have their own mount (see NotificationHost). */
export abstract class AbstractPage<A = {}> implements m.ClassComponent<A> {
  abstract getPageTitle(): string;
  abstract render(vnode: m.Vnode<A, this>): m.Children;
  showToolbar():boolean {
    return true
  }
  /** Backs the toolbar's refresh icon, which spins until the returned promise settles. */
  refreshData?:()=>Promise<unknown> = undefined;

  view(vnode: m.Vnode<A, this>): m.Children {
    window.document.title = this.getPageTitle();
      return [
        this.renderDialog(),
        this.showToolbar() ? m(Toolbar, {refreshData:this.refreshData}) : null,
        this.render(vnode),
      ];
  }

  private renderDialog(): m.Children {
    let n = Dialogs.getCurrent();
    if (n) {
      return m(".dialogveil", n.render());
    } else {
      return null;
    }
  }
}



