import m from 'mithril';

import { Children } from "mithril";
import { StatusOkIcon } from './atoms/icons/StatusOkIcon';
import { StatusInfoIcon } from './atoms/icons/StatusInfoIcon';
import { StatusWarningIcon } from './atoms/icons/StatusWarningIcon';
import { StatusErrorIcon } from './atoms/icons/StatusErrorIcon';
import { Notification, Notifications, NotificationsType } from '../utils/Notifications';
import { CloseIcon } from './atoms/icons/CloseIcon';
import { IconButton } from './IconButton';

export class NotificationComponent implements m.ClassComponent<NotificationComponentAttrs> {
  private timeout?:number = undefined;

  oninit({attrs}:m.Vnode<NotificationComponentAttrs>) {
    if (attrs.notification.timeout>0) {
      this.timeout = window.setTimeout(() => {
        this.timeout = undefined;
        Notifications.dismiss(attrs.notification);
        m.redraw();
      }, attrs.notification.timeout*1000);
    }
  }

  onbeforeremove({dom}:m.VnodeDOM<NotificationComponentAttrs>) {
    if (this.timeout !== undefined) {
      window.clearTimeout(this.timeout);
      this.timeout = undefined;
    }
    dom.classList.add("closed")
    return new Promise(function(resolve) {
        // Only its own removal animation: the countdown bar's animationend bubbles up here too.
        dom.addEventListener("animationend", (e) => {
          if (e.target === dom) resolve(undefined);
        })
    })
  }
  
  view({attrs}: m.Vnode<NotificationComponentAttrs>): void | Children {
    let n = attrs.notification;
    let icon:m.Children = "";
    let iconTitle = "";
    let typeClass = "";
    switch (n.type) {
      case NotificationsType.SUCCESS:
        icon = m(StatusOkIcon);
        iconTitle = "Success";
        typeClass = "type-success";
        break;
      case NotificationsType.INFO:
        icon = m(StatusInfoIcon);
        iconTitle = "Information";
        typeClass = "type-info";
        break;
      case NotificationsType.WARNING:
        icon = m(StatusWarningIcon);
        iconTitle = "Warning";
        typeClass = "type-warning";
        break;
      case NotificationsType.ERROR:
        icon = m(StatusErrorIcon);
        iconTitle = "Error";
        typeClass = "type-error";
        break;
    }
    let ad = n.alreadyDisplayed;
    n.alreadyDisplayed = true; 

    return m(".notificationO" + (ad?"":".appear"), m(".notification." + typeClass, [
      m(".typeicon", { title: iconTitle }, icon),
      m(".content", n.content),
      m(IconButton, { icon: CloseIcon, size: "small", class: "closebtn", title: "Dismiss", onclick: () => Notifications.dismiss() }),
      // Shrinks over the same duration as the auto-dismiss timer set in oninit.
      n.timeout > 0 ? m(".notification-countdown", { style: { animationDuration: n.timeout + "s" } }) : null,
    ]))
  }
}

export interface NotificationComponentAttrs {
  notification:Notification;
}