package net.pieroxy.mom.webserver;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TrustedProxiesTest {
  private static final TrustedProxies LOCAL_PROXY = TrustedProxies.parse(List.of("127.0.0.1", "::1", "172.18.0.0/16"));

  @Test
  public void matchesAddressesAndRanges() {
    assertTrue(LOCAL_PROXY.isTrusted("127.0.0.1"));
    assertTrue(LOCAL_PROXY.isTrusted("172.18.42.7"));
    assertFalse(LOCAL_PROXY.isTrusted("172.19.0.1"));
    assertTrue("Tomcat's long form of ::1", LOCAL_PROXY.isTrusted("0:0:0:0:0:0:0:1"));
    assertFalse(LOCAL_PROXY.isTrusted("203.0.113.7"));
    assertFalse("a host name is never resolved", LOCAL_PROXY.isTrusted("localhost"));
  }

  @Test
  public void matchesAPrefixThatIsNotAWholeNumberOfBytes() {
    TrustedProxies proxies = TrustedProxies.parse(List.of("10.0.0.0/12", "fd00::/7"));
    assertTrue(proxies.isTrusted("10.15.255.255"));
    assertFalse(proxies.isTrusted("10.16.0.0"));
    assertTrue(proxies.isTrusted("fc12::1"));
    assertFalse(proxies.isTrusted("fe80::1"));
  }

  @Test
  public void takesTheClientFromTheHeaderWhenTheConnectionComesFromATrustedProxy() {
    assertEquals("203.0.113.7", LOCAL_PROXY.clientAddress("127.0.0.1", "203.0.113.7"));
  }

  @Test
  public void ignoresTheHeaderFromAnUntrustedConnection() {
    assertEquals("a direct connection can't pick its own address",
        "198.51.100.1", LOCAL_PROXY.clientAddress("198.51.100.1", "203.0.113.7"));
    assertEquals("no proxies configured", "127.0.0.1", TrustedProxies.NONE.clientAddress("127.0.0.1", "203.0.113.7"));
  }

  @Test
  public void walksFromTheRightSoAForgedStartOfTheHeaderIsIgnored() {
    assertEquals("the client prepended a fake address; the proxy appended the real one",
        "203.0.113.7", LOCAL_PROXY.clientAddress("127.0.0.1", "1.2.3.4, 203.0.113.7"));
    assertEquals("several trusted hops are skipped",
        "203.0.113.7", LOCAL_PROXY.clientAddress("127.0.0.1", "1.2.3.4, 203.0.113.7, 172.18.0.5"));
  }

  @Test
  public void stopsAtAMalformedEntry() {
    assertEquals("127.0.0.1", LOCAL_PROXY.clientAddress("127.0.0.1", "unknown"));
    assertEquals("172.18.0.5", LOCAL_PROXY.clientAddress("127.0.0.1", "203.0.113.7, evil.example.com, 172.18.0.5"));
  }

  @Test
  public void stripsPortsAndBrackets() {
    assertEquals("203.0.113.7", LOCAL_PROXY.clientAddress("127.0.0.1", "203.0.113.7:51234"));
    assertEquals("2001:db8:0:0:0:0:0:1", LOCAL_PROXY.clientAddress("127.0.0.1", "[2001:db8::1]:443"));
  }

  @Test
  public void keepsTheConnectionAddressWithoutAHeader() {
    assertEquals("127.0.0.1", LOCAL_PROXY.clientAddress("127.0.0.1", null));
  }

  @Test
  public void rejectsWhatIsNeitherAnAddressNorARange() {
    for (String bad : List.of("localhost", "172.18.0.0/33", "::1/129", "10.0.0.0/x", "256.0.0.1", "")) {
      try {
        TrustedProxies.parse(List.of(bad));
        fail("should reject " + bad);
      } catch (IllegalArgumentException expected) {
        assertTrue(expected.getMessage(), expected.getMessage().contains("\"" + bad + "\""));
      }
    }
  }
}
