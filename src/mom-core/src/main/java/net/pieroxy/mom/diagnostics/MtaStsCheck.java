package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.detection.spf.SpfDnsException;
import net.pieroxy.mom.detection.spf.SpfDnsResolver;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Checks MTA-STS (RFC 8461) and TLS-RPT (RFC 8460) for a domain — both about mail sent <em>to</em>
 * it, both optional. MTA-STS: a {@code _mta-sts} TXT record announces a policy, served over HTTPS
 * at {@code https://mta-sts.<domain>/.well-known/mta-sts.txt}, telling sending servers to only
 * deliver to the listed MX hosts, over TLS with a valid certificate. So beyond the record and the
 * policy themselves, every MX must match the policy's {@code mx} patterns and present a certificate
 * that's valid for its name — checked by connecting to it on port 25 (partial when that's
 * impossible from where MOM runs). TLS-RPT: a {@code _smtp._tls} TXT record saying where sending
 * servers should report the TLS failures they meet, MTA-STS's (and DANE's) feedback channel.
 */
public class MtaStsCheck {
  private static final int MAX_AGE_LIMIT = 31_557_600; // RFC 8461 §3.2: about a year.
  private static final int MAX_AGE_RECOMMENDED_MIN = 86_400;
  private static final Pattern STS_ID = Pattern.compile("[A-Za-z0-9]{1,32}");

  /** The HTTP response to a policy request — see {@link #HTTPS}. */
  public record PolicyResponse(int status, String contentType, String body) {}

  public interface PolicyFetcher {
    /** Fetches {@code https://mta-sts.<domain>/.well-known/mta-sts.txt}; fails on any TLS or connection problem. */
    PolicyResponse fetch(String domain) throws IOException;
  }

  /** The real thing: HTTPS with the JDK's own certificate checks, and no redirects (RFC 8461 §3.3 forbids following them). */
  public static final PolicyFetcher HTTPS = new PolicyFetcher() {
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    @Override
    public PolicyResponse fetch(String domain) throws IOException {
      HttpRequest request = HttpRequest.newBuilder(URI.create("https://mta-sts." + domain + "/.well-known/mta-sts.txt"))
          .timeout(Duration.ofSeconds(10))
          .GET()
          .build();
      try {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new PolicyResponse(response.statusCode(), response.headers().firstValue("Content-Type").orElse(""), response.body());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("interrupted");
      }
    }
  };

  /** The JDK's default trusted certificate authorities. */
  public static X509TrustManager defaultTrust() {
    try {
      TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init((KeyStore) null);
      for (var tm : tmf.getTrustManagers()) {
        if (tm instanceof X509TrustManager x) return x;
      }
    } catch (Exception e) {
      throw new IllegalStateException("No default trust manager", e);
    }
    throw new IllegalStateException("No default trust manager");
  }

  private final SpfDnsResolver txtDns;
  private final MxDnsResolver mxDns;
  private final PolicyFetcher policies;
  private final StartTlsProbe.CertificateSource certificates;
  private final X509TrustManager trust;

  public MtaStsCheck(SpfDnsResolver txtDns, MxDnsResolver mxDns, PolicyFetcher policies,
                     StartTlsProbe.CertificateSource certificates, X509TrustManager trust) {
    this.txtDns = txtDns;
    this.mxDns = mxDns;
    this.policies = policies;
    this.certificates = certificates;
    this.trust = trust;
  }

  public DeliveryCheckResultDto run(String domain) {
    DeliveryCheckResultDto result = new DeliveryCheckResultDto(DeliveryCheck.MTA_STS).fact("Domain", domain);
    String mode = checkMtaSts(result, domain);
    boolean tlsRpt = checkTlsRpt(result, domain, mode != null);
    if (mode == null && !tlsRpt) return result.conclude("Neither MTA-STS nor TLS-RPT is set up for " + domain + " (optional).");
    if (mode == null) return result.conclude("MTA-STS isn't set up for " + domain + " (optional); TLS-RPT is.");
    return result.conclude(result.isPartial()
        ? "MTA-STS is set up for " + domain + " (mode " + mode + "; MX certificates not checked: see below)."
        : "MTA-STS is set up for " + domain + " (mode " + mode + ")" + (tlsRpt ? ", with TLS-RPT reporting." : "."));
  }

  /** @return the policy's mode, or null if MTA-STS isn't set up (or is too broken to apply). */
  private String checkMtaSts(DeliveryCheckResultDto result, String domain) {
    List<String> records;
    try {
      records = txt("_mta-sts." + domain, "v=STSv1");
    } catch (SpfDnsException e) {
      result.finding(CheckStatus.WARNING, "Could not look up _mta-sts." + domain + " (" + e.getMessage() + "). Try again later.");
      return null;
    }
    if (records.isEmpty()) {
      result.finding(CheckStatus.INFO, "MTA-STS isn't set up for " + domain + ". It's optional: it tells servers sending you mail "
          + "to always encrypt it and check your mail servers' certificates, instead of silently falling back to plain text when "
          + "an attacker interferes.");
      return null;
    }
    if (records.size() > 1) {
      records.forEach(r -> result.fact("MTA-STS record", r));
      result.finding(CheckStatus.ERROR, records.size() + " MTA-STS records at _mta-sts." + domain + ": senders ignore MTA-STS "
          + "entirely when there's more than one. Keep a single one.");
      return null;
    }
    result.fact("MTA-STS record", records.get(0));
    String id = tags(records.get(0)).get("id");
    if (id == null || !STS_ID.matcher(id).matches()) {
      result.finding(CheckStatus.ERROR, "The MTA-STS record has " + (id == null ? "no id=" : "an invalid id=" + id) + ": senders "
          + "ignore it. Use 1 to 32 letters and digits, and change it whenever the policy changes.");
      return null;
    }

    String url = "https://mta-sts." + domain + "/.well-known/mta-sts.txt";
    result.fact("Policy URL", url);
    PolicyResponse response;
    try {
      response = policies.fetch(domain);
    } catch (IOException e) {
      result.finding(CheckStatus.ERROR, "Senders can't fetch the MTA-STS policy at " + url + " (" + e.getMessage() + "): they'll "
          + "ignore MTA-STS — or, if they had cached a policy, may refuse to deliver once it expires. It must be served over HTTPS "
          + "with a valid certificate for mta-sts." + domain + ".");
      return null;
    }
    if (response.status() != 200) {
      result.finding(CheckStatus.ERROR, "The MTA-STS policy at " + url + " answers HTTP " + response.status()
          + (response.status() / 100 == 3 ? " (a redirect, which senders don't follow)" : "") + ": senders can't use it. Serve it "
          + "directly, with status 200.");
      return null;
    }
    if (!response.contentType().toLowerCase(Locale.ROOT).startsWith("text/plain")) {
      result.finding(CheckStatus.WARNING, "The MTA-STS policy is served as \"" + response.contentType() + "\" instead of text/plain: "
          + "some senders reject it.");
    }

    Map<String, List<String>> policy = parsePolicy(response.body());
    String version = first(policy, "version");
    String mode = first(policy, "mode");
    String maxAge = first(policy, "max_age");
    List<String> mxPatterns = policy.getOrDefault("mx", List.of());
    result.fact("Policy", "mode: " + mode + ", max_age: " + maxAge + ", mx: " + String.join(", ", mxPatterns));
    if (!"STSv1".equals(version)) {
      result.finding(CheckStatus.ERROR, "The MTA-STS policy has " + (version == null ? "no version" : "version \"" + version + "\"")
          + ": it must start with \"version: STSv1\", senders ignore it otherwise.");
      return null;
    }
    if (mode == null || !List.of("enforce", "testing", "none").contains(mode)) {
      result.finding(CheckStatus.ERROR, "The MTA-STS policy has " + (mode == null ? "no mode" : "an invalid mode \"" + mode + "\"")
          + ": use enforce, testing or none.");
      return null;
    }
    Integer age = parseInt(maxAge);
    if (age == null || age < 0 || age > MAX_AGE_LIMIT) {
      result.finding(CheckStatus.ERROR, "The MTA-STS policy's max_age " + (maxAge == null ? "is missing" : "\"" + maxAge + "\" is invalid")
          + ": it must be a number of seconds, at most " + MAX_AGE_LIMIT + " (one year).");
      return null;
    }
    if (!mode.equals("none") && mxPatterns.isEmpty()) {
      result.finding(CheckStatus.ERROR, "The MTA-STS policy lists no mx: senders can deliver to none of your mail servers under it.");
      return null;
    }

    result.finding(CheckStatus.OK, "Valid MTA-STS policy found.");
    switch (mode) {
      case "enforce" -> result.finding(CheckStatus.OK, "Mode enforce: senders only deliver to your listed mail servers, encrypted "
          + "and with a valid certificate.");
      case "testing" -> result.finding(CheckStatus.WARNING, "Mode testing: senders report failures (see TLS-RPT) but still deliver, "
          + "so it doesn't protect anything yet. Switch to enforce once reports show no failures.");
      default -> result.finding(CheckStatus.WARNING, "Mode none: MTA-STS is switched off. Usually a transition step before removing it.");
    }
    if (age < MAX_AGE_RECOMMENDED_MIN && !mode.equals("none")) {
      result.finding(CheckStatus.WARNING, "max_age is only " + age + " seconds: senders forget the policy that soon, which leaves a "
          + "window for attacks. A week or more (604800) is usual once it works.");
    }
    if (!mode.equals("none")) checkMxHosts(result, domain, mode, mxPatterns);
    return mode;
  }

  /** Every MX must match the policy, and present a certificate valid for its name. */
  private void checkMxHosts(DeliveryCheckResultDto result, String domain, String mode, List<String> patterns) {
    List<String> hosts = new ArrayList<>();
    try {
      for (MxDnsResolver.Mx mx : mxDns.lookupMx(domain)) {
        if (!mx.host().isEmpty()) hosts.add(mx.host().toLowerCase(Locale.ROOT));
      }
    } catch (MxDnsResolver.DnsLookupException e) {
      result.finding(CheckStatus.WARNING, "Could not look up " + domain + "'s MX records (" + e.getMessage() + ").");
      return;
    }
    // Under "testing", senders still deliver: a problem is worth knowing about, not yet breaking anything.
    CheckStatus severity = mode.equals("enforce") ? CheckStatus.ERROR : CheckStatus.WARNING;
    for (String host : hosts) {
      if (patterns.stream().noneMatch(p -> mxMatches(p, host))) {
        result.finding(severity, "The MX host " + host + " matches none of the policy's mx entries: senders applying it "
            + (mode.equals("enforce") ? "refuse" : "would refuse") + " to deliver to it. Add it to the policy (and change the id).");
      }
    }

    List<StartTlsProbe.Result> probes = StartTlsProbe.fetchAll(hosts, certificates);
    for (int i = 0; i < hosts.size(); i++) {
      String host = hosts.get(i);
      StartTlsProbe.Result probe = probes.get(i);
      if (probe.failure() instanceof StartTlsProbe.NoStartTlsException) {
        result.finding(severity, host + " doesn't offer STARTTLS: senders applying the policy "
            + (mode.equals("enforce") ? "refuse" : "would refuse") + " to deliver to it.");
      } else if (probe.failure() != null) {
        result.setPartial(true);
        result.finding(CheckStatus.INFO, "Couldn't connect to " + host + " on port 25 from here (" + probe.failure().getMessage()
            + "), so its certificate wasn't checked. Outgoing port 25 is often blocked on home connections.");
      } else {
        String problem = certificateProblem(probe.chain(), host);
        if (problem == null) {
          result.finding(CheckStatus.OK, host + " presents a valid certificate for its name.");
        } else {
          result.finding(severity, host + "'s certificate " + problem + ": senders applying the policy "
              + (mode.equals("enforce") ? "refuse" : "would refuse") + " to deliver to it.");
        }
      }
    }
  }

  /** @return what's wrong with the certificate for MTA-STS (RFC 8461 §4.2: trusted, valid now, for this name), or null. */
  String certificateProblem(List<X509Certificate> chain, String host) {
    if (chain == null || chain.isEmpty()) return "is missing";
    try {
      trust.checkServerTrusted(chain.toArray(new X509Certificate[0]), "UNKNOWN");
    } catch (CertificateException e) {
      return "isn't trusted (" + e.getMessage() + ")";
    }
    if (!validFor(chain.get(0), host)) return "isn't valid for the name " + host;
    return null;
  }

  private static boolean validFor(X509Certificate cert, String host) {
    try {
      if (cert.getSubjectAlternativeNames() == null) return false;
      for (List<?> san : cert.getSubjectAlternativeNames()) {
        if (Integer.valueOf(2).equals(san.get(0)) && mxMatches(san.get(1).toString().toLowerCase(Locale.ROOT), host)) return true;
      }
    } catch (CertificateParsingException e) {
      return false;
    }
    return false;
  }

  /** "mail.example.org" matches itself; "*.example.org" matches one more label, e.g. "mx1.example.org" (RFC 8461 §4.1). */
  static boolean mxMatches(String pattern, String host) {
    String p = pattern.toLowerCase(Locale.ROOT);
    String h = host.toLowerCase(Locale.ROOT);
    if (p.startsWith("*.")) {
      int dot = h.indexOf('.');
      return dot > 0 && h.substring(dot + 1).equals(p.substring(2));
    }
    return p.equals(h);
  }

  /** @return whether TLS-RPT is set up. */
  private boolean checkTlsRpt(DeliveryCheckResultDto result, String domain, boolean mtaSts) {
    List<String> records;
    try {
      records = txt("_smtp._tls." + domain, "v=TLSRPTv1");
    } catch (SpfDnsException e) {
      result.finding(CheckStatus.WARNING, "Could not look up _smtp._tls." + domain + " (" + e.getMessage() + "). Try again later.");
      return false;
    }
    if (records.isEmpty()) {
      result.finding(CheckStatus.INFO, "TLS-RPT isn't set up for " + domain + (mtaSts
          ? ": you won't hear about senders failing to deliver to you securely, so a broken MTA-STS setup would go unnoticed. Add "
          + "a TXT record at _smtp._tls." + domain + " like \"v=TLSRPTv1; rua=mailto:tls-reports@" + domain + "\"."
          : ". It's optional: it gets you reports from servers that had trouble delivering to you securely."));
      return false;
    }
    if (records.size() > 1) {
      result.finding(CheckStatus.ERROR, records.size() + " TLS-RPT records at _smtp._tls." + domain + ": senders send no report "
          + "when there's more than one. Keep a single one.");
      return false;
    }
    result.fact("TLS-RPT record", records.get(0));
    String rua = tags(records.get(0)).get("rua");
    if (rua == null || rua.isBlank()) {
      result.finding(CheckStatus.ERROR, "The TLS-RPT record has no rua=: senders don't know where to send reports.");
      return false;
    }
    for (String destination : rua.split(",")) {
      String d = destination.trim().toLowerCase(Locale.ROOT);
      if (!d.startsWith("mailto:") && !d.startsWith("https:")) {
        result.finding(CheckStatus.ERROR, "The TLS-RPT destination \"" + destination.trim() + "\" is invalid: use mailto: or https:.");
        return false;
      }
    }
    result.finding(CheckStatus.OK, "TLS-RPT is set up: reports go to " + rua + ".");
    return true;
  }

  private List<String> txt(String name, String version) throws SpfDnsException {
    return txtDns.lookupTxt(name).stream()
        .filter(t -> t.regionMatches(true, 0, version, 0, version.length())
            && (t.length() == version.length() || t.charAt(version.length()) == ';' || Character.isWhitespace(t.charAt(version.length()))))
        .toList();
  }

  private static Map<String, String> tags(String record) {
    Map<String, String> tags = new LinkedHashMap<>();
    for (String part : record.split(";")) {
      int eq = part.indexOf('=');
      if (eq > 0) tags.put(part.substring(0, eq).trim().toLowerCase(Locale.ROOT), part.substring(eq + 1).trim());
    }
    return tags;
  }

  /** "key: value" lines; mx may repeat. */
  private static Map<String, List<String>> parsePolicy(String body) {
    Map<String, List<String>> policy = new LinkedHashMap<>();
    for (String line : body.split("\r?\n")) {
      int colon = line.indexOf(':');
      if (colon > 0) {
        policy.computeIfAbsent(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
            .add(line.substring(colon + 1).trim());
      }
    }
    return policy;
  }

  private static String first(Map<String, List<String>> policy, String key) {
    List<String> values = policy.get(key);
    return values == null || values.isEmpty() ? null : values.get(0);
  }

  private static Integer parseInt(String s) {
    try {
      return s == null ? null : Integer.valueOf(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
