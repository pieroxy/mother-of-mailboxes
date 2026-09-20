package net.pieroxy.imf.detection.fcrdns;

import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.PTRRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.ReverseMap;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** {@link FcrdnsDnsResolver} implementation backed by dnsjava. */
public class DnsJavaFcrdnsDnsResolver implements FcrdnsDnsResolver {
  private final Resolver resolver;

  public DnsJavaFcrdnsDnsResolver() {
    this(Duration.ofSeconds(5));
  }

  public DnsJavaFcrdnsDnsResolver(Duration timeout) {
    try {
      SimpleResolver simpleResolver = new SimpleResolver();
      simpleResolver.setTimeout(timeout);
      this.resolver = simpleResolver;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Could not initialize DNS resolver", e);
    }
  }

  @Override
  public List<String> lookupPtr(String ip) throws FcrdnsDnsException {
    Name reverseName;
    try {
      reverseName = ReverseMap.fromAddress(ip);
    } catch (UnknownHostException e) {
      throw new FcrdnsDnsException("Invalid IP literal: " + ip, e);
    }
    List<String> result = new ArrayList<>();
    for (Record record : execute(new Lookup(reverseName, Type.PTR), "PTR " + ip)) {
      if (record instanceof PTRRecord) {
        result.add(((PTRRecord) record).getTarget().toString());
      }
    }
    return result;
  }

  @Override
  public List<String> lookupA(String hostname) throws FcrdnsDnsException {
    List<String> result = new ArrayList<>();
    for (Record record : execute(lookup(hostname, Type.A), "A " + hostname)) {
      if (record instanceof ARecord) {
        result.add(((ARecord) record).getAddress().getHostAddress());
      }
    }
    return result;
  }

  @Override
  public List<String> lookupAaaa(String hostname) throws FcrdnsDnsException {
    List<String> result = new ArrayList<>();
    for (Record record : execute(lookup(hostname, Type.AAAA), "AAAA " + hostname)) {
      if (record instanceof AAAARecord) {
        result.add(((AAAARecord) record).getAddress().getHostAddress());
      }
    }
    return result;
  }

  private Lookup lookup(String name, int type) throws FcrdnsDnsException {
    try {
      return new Lookup(name, type);
    } catch (TextParseException e) {
      throw new FcrdnsDnsException("Invalid hostname: " + name, e);
    }
  }

  private List<Record> execute(Lookup lookup, String description) throws FcrdnsDnsException {
    lookup.setResolver(resolver);
    Record[] answers = lookup.run();
    int status = lookup.getResult();
    // HOST_NOT_FOUND (NXDOMAIN) and TYPE_NOT_FOUND (NODATA) are normal DNS answers
    // ("nothing of this type here"), not failures: return an empty list.
    if (status == Lookup.HOST_NOT_FOUND || status == Lookup.TYPE_NOT_FOUND) {
      return List.of();
    }
    if (status != Lookup.SUCCESSFUL) {
      throw new FcrdnsDnsException("DNS lookup of " + description + " failed: " + lookup.getErrorString());
    }
    return answers != null ? Arrays.asList(answers) : List.of();
  }
}
