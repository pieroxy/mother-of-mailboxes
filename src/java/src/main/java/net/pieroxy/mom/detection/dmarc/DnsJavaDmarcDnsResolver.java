package net.pieroxy.mom.detection.dmarc;

import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** {@link DmarcDnsResolver} implementation backed by dnsjava. */
public class DnsJavaDmarcDnsResolver implements DmarcDnsResolver {
  private final Resolver resolver;

  public DnsJavaDmarcDnsResolver() {
    this(Duration.ofSeconds(5));
  }

  public DnsJavaDmarcDnsResolver(Duration timeout) {
    try {
      SimpleResolver simpleResolver = new SimpleResolver();
      simpleResolver.setTimeout(timeout);
      this.resolver = simpleResolver;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Could not initialize DNS resolver", e);
    }
  }

  @Override
  public List<String> lookupTxt(String name) throws DmarcDnsException {
    List<String> result = new ArrayList<>();
    try {
      Lookup lookup = new Lookup(name, Type.TXT);
      lookup.setResolver(resolver);
      Record[] answers = lookup.run();
      int status = lookup.getResult();
      if (status == Lookup.HOST_NOT_FOUND || status == Lookup.TYPE_NOT_FOUND) {
        return result;
      }
      if (status != Lookup.SUCCESSFUL) {
        throw new DmarcDnsException("DNS lookup of TXT " + name + " failed: " + lookup.getErrorString());
      }
      if (answers != null) {
        for (Record record : answers) {
          if (record instanceof TXTRecord) {
            result.add(String.join("", ((TXTRecord) record).getStrings()));
          }
        }
      }
      return result;
    } catch (TextParseException e) {
      throw new DmarcDnsException("Invalid domain name: " + name, e);
    }
  }
}
