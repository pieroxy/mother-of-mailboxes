package net.pieroxy.mom.detection.fcrdns;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * FCrDNS (Forward-Confirmed reverse DNS) evaluator: checks that a connecting IP has a PTR
 * record (reverse DNS) that is confirmed by a forward (A/AAAA) resolution of the hostname
 * resolving back to the same IP.
 * <p>
 * Unlike SPF/DKIM/DMARC, this is not a domain authentication standard — the PTR record is
 * controlled by the owner of the IP block (the ISP/hosting provider), not by the sender's
 * domain. It's a signal about the legitimacy of the connecting infrastructure (a dynamic/
 * residential IP, typical of a botnet, usually has no forward-confirmed PTR), not proof that
 * the message actually comes from the domain it claims to represent.
 */
public class FcrdnsEvaluator {
  private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]+$");

  private final FcrdnsDnsResolver resolver;
  private final Logger defaultLogger = Logger.getLogger(FcrdnsEvaluator.class.getName());

  public FcrdnsEvaluator(FcrdnsDnsResolver resolver) {
    this.resolver = resolver;
  }

  /** @return the FCrDNS result for the connecting IP. */
  public FcrdnsResult evaluate(String ip) {
    return evaluate(ip, defaultLogger);
  }

  /** Like {@link #evaluate(String)}, but logs the details (FINE level) to the given logger. */
  public FcrdnsResult evaluate(String ip, Logger logger) {
    if (ip == null || !IP_LITERAL.matcher(ip).matches()) {
      logger.fine(() -> "Not a valid IP literal, cannot evaluate FCrDNS: " + ip);
      return FcrdnsResult.NONE;
    }
    InetAddress target;
    try {
      target = InetAddress.getByName(ip);
    } catch (UnknownHostException e) {
      logger.fine(() -> "Not a valid IP literal, cannot evaluate FCrDNS: " + ip);
      return FcrdnsResult.NONE;
    }
    try {
      List<String> ptrNames = resolver.lookupPtr(ip);
      if (ptrNames.isEmpty()) {
        logger.fine(() -> "No PTR record for " + ip);
        return FcrdnsResult.NONE;
      }
      boolean isV6 = target.getAddress().length == 16;
      for (String ptrName : ptrNames) {
        logger.fine(() -> "PTR for " + ip + ": " + ptrName);
        List<String> forwardAddresses = isV6 ? resolver.lookupAaaa(ptrName) : resolver.lookupA(ptrName);
        if (forwardConfirms(target, forwardAddresses)) {
          logger.fine(() -> "Forward-confirmed: " + ptrName + " resolves back to " + ip);
          return FcrdnsResult.PASS;
        }
      }
      logger.fine(() -> "PTR record(s) found for " + ip + " but none forward-confirm");
      return FcrdnsResult.FAIL;
    } catch (FcrdnsDnsException e) {
      logger.log(Level.FINE, "FCrDNS DNS lookup failed for " + ip, e);
      return FcrdnsResult.TEMPERROR;
    }
  }

  private static boolean forwardConfirms(InetAddress target, List<String> candidates) {
    for (String candidate : candidates) {
      try {
        // Compare via InetAddress, not string equality: two different textual representations
        // (notably compressed IPv6) can denote the same address.
        if (InetAddress.getByName(candidate).equals(target)) {
          return true;
        }
      } catch (UnknownHostException ignored) {
        // shouldn't happen: candidate comes from the resolver, already a valid literal
      }
    }
    return false;
  }
}
