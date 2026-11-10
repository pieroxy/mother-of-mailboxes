package net.pieroxy.mom.utils.mail;

import net.pieroxy.mom.config.general.MailAccountConfiguration;
import net.pieroxy.mom.config.general.SmtpConfiguration;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccountAddressesTest {
  @Test
  public void matchesAddressesAndDomainsIgnoringCase() {
    AccountAddresses addresses = new AccountAddresses(List.of("Contact@Example.com", "@example.net"));

    assertTrue(addresses.contains("contact@example.COM"));
    assertTrue(addresses.contains("anyone@Example.net"));
    assertFalse(addresses.contains("other@example.com"));
    assertFalse("a domain doesn't cover its subdomains", addresses.contains("me@sub.example.net"));
    assertFalse(addresses.contains(null));
  }

  @Test
  public void includesTheSmtpSenderAddress() {
    MailAccountConfiguration config = new MailAccountConfiguration();
    config.setAddresses(List.of("contact@example.com"));
    SmtpConfiguration smtp = new SmtpConfiguration();
    smtp.setFromAddress("me@example.com");
    config.setSmtp(smtp);

    AccountAddresses addresses = AccountAddresses.of(config);

    assertTrue(addresses.contains("contact@example.com"));
    assertTrue(addresses.contains("me@example.com"));
  }

  @Test
  public void worksWithoutAnythingConfigured() {
    assertFalse(AccountAddresses.of(new MailAccountConfiguration()).contains("me@example.com"));
  }

  @Test
  public void normalizesTrimmedLowerCasedAndDeduplicated() {
    assertEquals(List.of("contact@example.com", "@example.org"),
        AccountAddresses.normalize(Arrays.asList(" Contact@Example.com ", "@Example.org", "contact@example.com", " ", null)));
    assertEquals(List.of(), AccountAddresses.normalize(null));
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesAnInvalidAddress() {
    AccountAddresses.normalize(List.of("not an address"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesAnAddressWithoutDomain() {
    AccountAddresses.normalize(List.of("contact"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesAnInvalidDomain() {
    AccountAddresses.normalize(List.of("@localhost"));
  }
}
