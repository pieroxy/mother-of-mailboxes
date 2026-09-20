package net.pieroxy.imf.utils.mail;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import net.pieroxy.imf.config.credentials.Credential;
import net.pieroxy.imf.config.general.MailAccountConfiguration;
import net.pieroxy.imf.utils.scheduling.BackoffLoop;

import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link BackoffLoop.Waiter} that watches the INBOX via IMAP IDLE (RFC 2177) during what would
 * otherwise be a plain sleep between {@link net.pieroxy.imf.rules.MailAccount#processMessages()}
 * cycles, so new mail wakes the next cycle immediately instead of waiting out the full {@code
 * runEvery}. It does no processing itself — returning from {@link #await} just means "time for a
 * cycle now", whether that's because new mail showed up or because the wait budget ran out.
 * <p>
 * Uses its own short-lived connection, always closed before {@link #await} returns. This isn't
 * just tidiness: some IMAP servers refuse a second, concurrent {@code SELECT} of the same
 * mailbox by the same account, so this watcher must never still hold INBOX open when {@code
 * processMessages()}'s own connection tries to open it right after. Being a {@code Waiter}
 * (called in-line, in the same thread that runs {@code processMessages()}, right before it)
 * makes the two connections sequential by construction instead of relying on synchronization to
 * keep them apart.
 * <p>
 * The wait is sliced into short IDLE calls rather than one continuous {@code idle()} for the
 * whole budget: a blocked socket read can't be interrupted by {@code Thread.interrupt()}, so
 * without slicing, a slow server or a quiet mailbox could pin a wait in place for minutes with
 * no way to cut it short. Slicing bounds that to {@link #SLICE_MS} on its own; {@link
 * #interruptNow()} (see {@code Runner.shutdown()}) closes it out immediately instead of waiting
 * for the slice to run out. Also stays well under the ~29 minute IDLE duration servers are
 * recommended to tolerate (RFC 2177).
 */
public class ImapIdleWatcher implements BackoffLoop.Waiter {
  private final static Logger LOGGER = Logger.getLogger(ImapIdleWatcher.class.getName());
  private final static long SLICE_MS = 2 * 60 * 1000L;

  private final MailAccountConfiguration config;
  private final Credential credential;
  private final ImapStoreConnector storeConnector;
  // Once a server proves it doesn't support IDLE, that's a static property of the server: no
  // point reconnecting to re-ask on every single wait for the rest of the process's lifetime.
  private volatile boolean idleUnsupported;
  // Set for as long as idleForOneSlice() is actually blocked in idle() — read from another
  // thread by interruptNow(), so it can close this specific connection out from under it.
  private volatile Store activeStore;

  public ImapIdleWatcher(MailAccountConfiguration config, Credential credential) {
    this(config, credential, ImapIdleWatcher::connectImaps);
  }

  /** Visible for tests: lets a store connector be injected without real IMAPS/TLS. */
  ImapIdleWatcher(MailAccountConfiguration config, Credential credential, ImapStoreConnector storeConnector) {
    this.config = config;
    this.credential = credential;
    this.storeConnector = storeConnector;
  }

  private static Store connectImaps(MailAccountConfiguration config, Credential credential) throws MessagingException {
    Session session = Session.getDefaultInstance(new Properties());
    Store store = session.getStore("imaps");
    store.connect(config.getHost(), config.getPort(), credential.getUsername(), credential.getPassword());
    return store;
  }

  @Override
  public void await(long delayMs) throws InterruptedException {
    if (idleUnsupported) {
      Thread.sleep(delayMs);
      return;
    }
    long deadline = System.currentTimeMillis() + delayMs;
    while (System.currentTimeMillis() < deadline) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
      long sliceMs = Math.min(deadline - System.currentTimeMillis(), SLICE_MS);
      try {
        if (idleForOneSlice(sliceMs)) return; // new mail: let the caller run a cycle right now
      } catch (MessagingException e) {
        LOGGER.log(Level.FINE, "IDLE watcher [" + config.getDisplayName() + "]: wait failed ("
            + e.getMessage() + "), falling back to a plain wait for the rest of this cycle.", e);
        Thread.sleep(Math.max(0, deadline - System.currentTimeMillis()));
        return;
      }
      if (idleUnsupported) {
        Thread.sleep(Math.max(0, deadline - System.currentTimeMillis()));
        return;
      }
      // Otherwise just this slice's timeout, no new mail: loop again if budget remains.
    }
  }

  /**
   * Forces whichever slice is currently blocked in {@code idle()}, if any, to return right away —
   * by closing its connection out from under it, the same way a new-mail notification or a slice
   * timeout already do (see {@link #idleForOneSlice}). Safe to call from another thread; a no-op
   * if no slice is in progress right now (e.g. between slices, or mid-{@code processMessages()}).
   */
  public void interruptNow() {
    Store store = activeStore;
    if (store != null) {
      closeQuietly(store);
    }
  }

  /** @return true if new mail arrived in the INBOX during this slice. */
  private boolean idleForOneSlice(long sliceMs) throws MessagingException {
    Store store = storeConnector.connect(config, credential);
    activeStore = store;
    try {
      if (!((IMAPStore) store).hasCapability("IDLE")) {
        LOGGER.warning("IDLE watcher [" + config.getDisplayName() + "]: server does not advertise IDLE "
            + "support, giving up for this account (new mail will still be picked up by the regular runEvery cycle).");
        idleUnsupported = true;
        return false;
      }

      IMAPFolder inbox = (IMAPFolder) store.getFolder("INBOX");
      inbox.open(Folder.READ_ONLY);

      // idle() does not reliably return on its own when new mail arrives, so the listener itself
      // forces it to unblock (by closing the connection) rather than waiting for idle() to
      // return naturally.
      AtomicBoolean newMail = new AtomicBoolean(false);
      inbox.addMessageCountListener(new MessageCountAdapter() {
        @Override
        public void messagesAdded(MessageCountEvent e) {
          newMail.set(true); // happens-before the close() below, so no race reading it after idle() unblocks
          closeQuietly(store);
        }
      });

      // Forces idle() to return once this slice's budget is up if no mail shows up first — same
      // mechanism as the listener above and as a deliberate shutdown: closing the connection
      // breaks the blocking read idle() is waiting on.
      Timer sliceTimer = new Timer("idle-watcher-slice-" + config.getDisplayName(), true);
      sliceTimer.schedule(new TimerTask() {
        @Override
        public void run() {
          closeQuietly(store);
        }
      }, sliceMs);
      try {
        inbox.idle();
      } catch (MessagingException e) {
        // Either of the two closes above, or a genuine connection drop — either way this slice
        // is over; a real connectivity problem still surfaces on the next processMessages()
        // connection attempt.
      } finally {
        sliceTimer.cancel();
      }
      return newMail.get();
    } finally {
      activeStore = null;
      closeQuietly(store);
    }
  }

  private void closeQuietly(Store store) {
    try {
      if (store.isConnected()) store.close();
    } catch (MessagingException e) {
      LOGGER.log(Level.FINE, "Error closing IDLE watcher connection", e);
    }
  }
}
