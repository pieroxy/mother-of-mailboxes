import m from "mithril";
import { Toolbar } from "../Toolbar";
import { Notifications } from "../../utils/Notifications";
import { NotificationComponent } from "../NotificationComponent";
import { Dialogs } from "../../utils/Dialogs";

/** Page base: sets the browser tab title and wraps render() with the shared chrome (notification, dialog, toolbar). */
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
        this.renderNotification(),
        this.renderDialog(),
        this.showToolbar() ? m(Toolbar, {refreshData:this.refreshData}) : null,
        this.render(vnode),
      ];
  }

  private renderNotification(): m.Children {
    let n = Notifications.getTopNotification();
    if (n) {
      return m(NotificationComponent, {notification:n})
    } else {
      return null;
    }
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



