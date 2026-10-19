package net.pieroxy.mom.diagnostics;

import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** {@link MxDnsResolver} backed by dnsjava, using the system's DNS resolver. */
public class DnsJavaMxDnsResolver implements MxDnsResolver {
  private final SimpleResolver resolver;

  public DnsJavaMxDnsResolver() {
    try {
      resolver = new SimpleResolver();
      resolver.setTimeout(Duration.ofSeconds(5));
    } catch (IOException e) {
      throw new IllegalStateException("Could not initialize the DNS resolver", e);
    }
  }

  @Override
  public List<Mx> lookupMx(String domain) throws DnsLookupException {
    List<Mx> result = new ArrayList<>();
    for (Record r : lookup(domain, Type.MX)) {
      if (r instanceof MXRecord mx) result.add(new Mx(mx.getPriority(), strip(mx.getTarget().toString())));
    }
    return result;
  }

  @Override
  public List<String> lookupCname(String name) throws DnsLookupException {
    List<String> result = new ArrayList<>();
    for (Record r : lookup(name, Type.CNAME)) {
      if (r instanceof CNAMERecord cname) result.add(strip(cname.getTarget().toString()));
    }
    return result;
  }

  @Override
  public List<String> lookupA(String name) throws DnsLookupException {
    List<String> result = new ArrayList<>();
    for (Record r : lookup(name, Type.A)) {
      if (r instanceof ARecord a) result.add(a.getAddress().getHostAddress());
    }
    return result;
  }

  @Override
  public List<String> lookupAaaa(String name) throws DnsLookupException {
    List<String> result = new ArrayList<>();
    for (Record r : lookup(name, Type.AAAA)) {
      if (r instanceof AAAARecord a) result.add(a.getAddress().getHostAddress());
    }
    return result;
  }

  private Record[] lookup(String name, int type) throws DnsLookupException {
    try {
      Lookup lookup = new Lookup(name, type);
      lookup.setResolver(resolver);
      Record[] answers = lookup.run();
      int result = lookup.getResult();
      if (result == Lookup.HOST_NOT_FOUND || result == Lookup.TYPE_NOT_FOUND) return new Record[0];
      if (result != Lookup.SUCCESSFUL) {
        throw new DnsLookupException("DNS lookup of " + Type.string(type) + " " + name + " failed: " + lookup.getErrorString());
      }
      return answers != null ? answers : new Record[0];
    } catch (TextParseException e) {
      throw new DnsLookupException("Invalid name: " + name);
    }
  }

  /** "mx.example.org." → "mx.example.org", "." (null MX) → "". */
  private static String strip(String name) {
    return name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
  }
}
