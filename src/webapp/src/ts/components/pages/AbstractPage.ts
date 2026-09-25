import m from "mithril";
import { Toolbar } from "../Toolbar";
import { Notifications } from "../../utils/Notifications";
import { NotificationComponent } from "../NotificationComponent";
import { Dialogs } from "../../utils/Dialogs";

/**
 * Minimal page base: just the browser tab title and a render hook. Deliberately doesn't bring in
 * shared chrome (nav bar, notifications, dialogs) yet — there's only two pages so far; that's
 * worth adding once there's enough of them to actually need navigating between.
 */
export abstract class AbstractPage<A = {}> implements m.ClassComponent<A> {
  abstract getPageTitle(): string;
  abstract render(vnode: m.Vnode<A, this>): m.Children;
  showToolbar():boolean {
    return true
  }
  refreshData?:()=>void = undefined;

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



