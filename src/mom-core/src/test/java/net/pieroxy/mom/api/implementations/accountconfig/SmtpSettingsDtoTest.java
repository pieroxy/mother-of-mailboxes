package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.config.general.SmtpConfiguration;
import net.pieroxy.mom.config.general.SmtpSecurity;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SmtpSettingsDtoTest {
  private static SmtpSettingsDto dto() {
    SmtpSettingsDto dto = new SmtpSettingsDto();
    dto.setHost(" smtp.example.com ");
    dto.setPort(587);
    dto.setSecurity(SmtpSecurity.STARTTLS);
    dto.setFromAddress("me@example.com");
    return dto;
  }

  @Test
  public void convertsTrimmed() {
    SmtpConfiguration smtp = dto().toConfiguration();

    assertEquals("smtp.example.com", smtp.getHost());
    assertEquals("me@example.com", smtp.getFromAddress());
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesAnInvalidSenderAddress() {
    SmtpSettingsDto dto = dto();
    dto.setFromAddress("nope");
    dto.toConfiguration();
  }
}
