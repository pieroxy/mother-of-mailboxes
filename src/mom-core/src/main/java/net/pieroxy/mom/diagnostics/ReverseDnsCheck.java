package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.detection.fcrdns.FcrdnsDnsException;
import net.pieroxy.mom.detection.fcrdns.FcrdnsDnsResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Checks the server that delivered the test message (see {@link TestSends}): that its IP has a
 * reverse DNS name (PTR) resolving back to that same IP — forward-confirmed reverse DNS, the same
 * check MOM runs on incoming mail ({@code FcrdnsEvaluator}) and that many receivers require — that
 * this name doesn't look like a residential or dynamic connection's, and that the name the server
 * announced in its HELO/EHLO is a real host name, consistent with it. Nothing to check without a
 * test message: only that tells which server actually delivers the account's mail.
 */
public class ReverseDnsCheck {
  // Words ISPs put in the reverse DNS of residential and dynamic connections.
  private static final Pattern GENERIC_WORDS = Pattern.compile(
      "(^|[.-])(dyn|dynamic|dhcp|dsl|adsl|vdsl|xdsl|cable|ppp|pppoe|pool|customer|cust|subs|broadband|residential)[0-9]*([.-]|$)");

  private final FcrdnsDnsResolver resolver;

  public ReverseDnsCheck(FcrdnsDnsResolver resolver) {
    this.resolver = resolver;
  }

  public DeliveryCheckResultDto run(Supplier<TestSendOutcome> testSend) {
    TestSendOutcome outcome = testSend.get();
    switch (outcome.kind()) {
      case PENDING:
        return DeliveryCheckResultDto.inProgress(DeliveryCheck.REVERSE_DNS, outcome.message());
      case NOT_CONFIGURED:
        return DeliveryCheckResultDto.skipped(DeliveryCheck.REVERSE_DNS, "Needs an SMTP sink: only a message actually sent tells "
            + "which server delivers your mail. Set it in General settings.");
      case FAILED:
        return DeliveryCheckResultDto.skipped(DeliveryCheck.REVERSE_DNS, "Needs the test message, but the test send failed: " + outcome.message());
      default:
        return check(outcome.received().clientIp(), outcome.received().helo());
    }
  }

  private DeliveryCheckResultDto check(String ip, String helo) {
    DeliveryCheckResultDto result = new DeliveryCheckResultDto(DeliveryCheck.REVERSE_DNS)
        .fact("Sending server", ip)
        .fact("HELO name", helo);

    String confirmedName = checkReverseDns(result, ip);
    checkHelo(result, ip, helo, confirmedName);
    return result.conclude("The server sending your mail has a proper reverse DNS name"
        + (confirmedName != null ? " (" + confirmedName + ")" : "") + ", consistent with its HELO name.");
  }

  /** @return the forward-confirmed reverse DNS name of {@code ip}, or null if it has none. */
  private String checkReverseDns(DeliveryCheckResultDto result, String ip) {
    List<String> names;
    try {
      names = resolver.lookupPtr(ip).stream().map(ReverseDnsCheck::normalize).toList();
    } catch (FcrdnsDnsException e) {
      result.finding(CheckStatus.WARNING, "Could not look up the reverse DNS of " + ip + " (" + e.getMessage() + "). Try again later.");
      return null;
    }
    if (names.isEmpty()) {
      result.finding(CheckStatus.ERROR, ip + " has no reverse DNS (PTR record): many receiving servers, Gmail and Outlook "
          + "among them, refuse or penalize mail from such addresses. Ask whoever provides that IP (hosting provider, ISP) to "
          + "set its reverse DNS to your mail server's name.");
      return null;
    }
    result.fact("Reverse DNS (PTR)", String.join(", ", names));
    if (names.size() > 1) {
      result.finding(CheckStatus.INFO, ip + " has " + names.size() + " PTR records: one is usual, some receivers only look at the first.");
    }

    String confirmed = null;
    for (String name : names) {
      List<String> forward = resolve(name, ip.contains(":"));
      if (forward == null) {
        result.finding(CheckStatus.WARNING, "Could not resolve " + name + " (DNS failure). Try again later.");
        return null;
      }
      if (contains(forward, ip)) {
        confirmed = name;
        break;
      }
    }
    if (confirmed == null) {
      result.finding(CheckStatus.ERROR, "The reverse DNS of " + ip + " (" + String.join(", ", names) + ") doesn't resolve back "
          + "to " + ip + ": receivers checking forward-confirmed reverse DNS treat it as missing. Add an "
          + (ip.contains(":") ? "AAAA" : "A") + " record for that name pointing to " + ip + ", or have the PTR changed.");
      return null;
    }
    result.finding(CheckStatus.OK, ip + " has a reverse DNS name, " + confirmed + ", which resolves back to it.");
    if (looksGeneric(confirmed, ip)) {
      result.finding(CheckStatus.WARNING, confirmed + " looks like the generic name of a residential or dynamic connection: "
          + "many receivers treat mail coming straight from such addresses as spam. Have the reverse DNS set to your mail "
          + "server's name, or send through your provider's or ISP's relay.");
    }
    return confirmed;
  }

  private void checkHelo(DeliveryCheckResultDto result, String ip, String helo, String confirmedName) {
    String name = normalize(helo);
    if (name.startsWith("[")) {
      result.finding(CheckStatus.WARNING, "The server introduces itself (HELO) with an IP address, " + helo + ", instead of its "
          + "name: allowed, but frowned upon by many receivers. Configure it to use " + (confirmedName != null ? confirmedName
          : "its fully qualified name") + ".");
      return;
    }
    if (!name.contains(".") || name.equals("localhost") || name.endsWith(".localdomain") || name.endsWith(".local")
        || name.endsWith(".lan") || name.endsWith(".home") || name.endsWith(".internal")) {
      result.finding(CheckStatus.ERROR, "The server introduces itself (HELO) as \"" + helo + "\", which isn't a public fully "
          + "qualified domain name: many receivers reject such mail outright. Configure it to use "
          + (confirmedName != null ? confirmedName : "a real name that resolves to " + ip) + ".");
      return;
    }
    if (name.equals(confirmedName)) {
      result.finding(CheckStatus.OK, "The HELO name matches the reverse DNS name.");
      return;
    }
    List<String> forward = resolve(name, ip.contains(":"));
    if (forward == null) {
      result.finding(CheckStatus.WARNING, "Could not resolve the HELO name " + helo + " (DNS failure). Try again later.");
    } else if (contains(forward, ip)) {
      result.finding(CheckStatus.OK, "The HELO name " + helo + " resolves to " + ip + ".");
      if (confirmedName != null) {
        result.finding(CheckStatus.INFO, "The HELO name differs from the reverse DNS name (" + confirmedName + "): fine for most "
            + "receivers, but using the same name everywhere is the safest.");
      }
    } else {
      result.finding(CheckStatus.WARNING, "The HELO name " + helo + (forward.isEmpty() ? " doesn't resolve" : " doesn't resolve to " + ip)
          + ", nor matches the reverse DNS: some receivers penalize such mail. Configure the server to introduce itself as "
          + (confirmedName != null ? confirmedName : "a name that resolves to " + ip) + ".");
    }
  }

  /** @return the addresses of {@code name} in the same family as the sending IP, or null on a DNS failure. */
  private List<String> resolve(String name, boolean v6) {
    try {
      return v6 ? resolver.lookupAaaa(name) : resolver.lookupA(name);
    } catch (FcrdnsDnsException e) {
      return null;
    }
  }

  private static boolean contains(List<String> addresses, String ip) {
    try {
      InetAddress target = InetAddress.getByName(ip);
      for (String a : addresses) {
        if (InetAddress.getByName(a).equals(target)) return true;
      }
    } catch (UnknownHostException e) {
      // Not literals: can't match.
    }
    return false;
  }

  /**
   * A residential/dynamic connection's name: one embedding the IP's digits (e.g.
   * "82-65-93-243.subs.proxad.net", "243.93.65.82.example.net") or the words ISPs use for them.
   */
  static boolean looksGeneric(String name, String ip) {
    String n = name.toLowerCase(Locale.ROOT);
    if (!ip.contains(":")) {
      String[] o = ip.split("\\.");
      if (o.length == 4) {
        List<String> forms = new ArrayList<>();
        for (String sep : new String[]{"-", ".", "_", ""}) {
          forms.add(String.join(sep, o));
          forms.add(o[3] + sep + o[2] + sep + o[1] + sep + o[0]);
        }
        for (String form : forms) {
          if (n.contains(form)) return true;
        }
      }
    }
    return GENERIC_WORDS.matcher(n).find();
  }

  private static String normalize(String name) {
    String n = name.trim().toLowerCase(Locale.ROOT);
    return n.endsWith(".") ? n.substring(0, n.length() - 1) : n;
  }
}
