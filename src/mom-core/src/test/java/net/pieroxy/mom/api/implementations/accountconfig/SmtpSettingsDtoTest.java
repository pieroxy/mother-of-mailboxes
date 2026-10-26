package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.config.general.SmtpConfiguration;
import net.pieroxy.mom.config.general.SmtpSecurity;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SmtpSettingsDtoTest {
  private static SmtpSettingsDto dto(String... aliases) {
    SmtpSettingsDto dto = new SmtpSettingsDto();
    dto.setHost(" smtp.example.com ");
    dto.setPort(587);
    dto.setSecurity(SmtpSecurity.STARTTLS);
    dto.setFromAddress("me@example.com");
    dto.setAliases(Arrays.asList(aliases));
    return dto;
  }

  @Test
  public void convertsTrimmed() {
    SmtpConfiguration smtp = dto().toConfiguration();

    assertEquals("smtp.example.com", smtp.getHost());
    assertEquals("me@example.com", smtp.getFromAddress());
  }

  @Test
  public void noAliasesGiveNoList() {
    assertNull(dto().toConfiguration().getAliases());
    assertNull(dto(" ", "").toConfiguration().getAliases());
  }

  @Test
  public void aliasesAreTrimmedLowerCasedAndDeduplicated() {
    SmtpConfiguration smtp = dto(" Contact@Example.com ", "@Example.org", "contact@example.com").toConfiguration();

    assertEquals(List.of("contact@example.com", "@example.org"), smtp.getAliases());
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesAnInvalidAddressAlias() {
    dto("not an address").toConfiguration();
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesAnAliasWithoutDomain() {
    dto("contact").toConfiguration();
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesAnInvalidDomainAlias() {
    dto("@localhost").toConfiguration();
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesAnInvalidSenderAddress() {
    SmtpSettingsDto dto = dto();
    dto.setFromAddress("nope");
    dto.toConfiguration();
  }
}
