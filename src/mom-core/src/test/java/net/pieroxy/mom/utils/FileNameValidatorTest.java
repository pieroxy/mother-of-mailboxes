package net.pieroxy.mom.utils;

import org.junit.Test;

import static org.junit.Assert.fail;

public class FileNameValidatorTest {

  private void expectRejected(String name) {
    try {
      FileNameValidator.validate(name, "displayName");
      fail("should have rejected \"" + name + "\"");
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }

  @Test
  public void acceptsOrdinaryNames() {
    FileNameValidator.validate("personal", "displayName");
    FileNameValidator.validate("Work Account", "displayName");
    FileNameValidator.validate("bob.smith_2024", "displayName");
    FileNameValidator.validate("Éléonore", "displayName");
  }

  @Test
  public void rejectsNullOrBlank() {
    expectRejected(null);
    expectRejected("");
    expectRejected("   ");
  }

  @Test
  public void rejectsDotAndDotDot() {
    expectRejected(".");
    expectRejected("..");
  }

  @Test
  public void rejectsPathSeparators() {
    expectRejected("a/b");
    expectRejected("a\\b");
  }

  @Test
  public void rejectsNtfsForbiddenCharacters() {
    for (char c : "<>:\"/\\|?*".toCharArray()) {
      expectRejected("account" + c);
    }
  }

  @Test
  public void rejectsControlCharacters() {
    expectRejected("account\u0007");
    expectRejected("account\ttab");
  }

  @Test
  public void rejectsTrailingSpaceOrPeriod() {
    expectRejected("account ");
    expectRejected("account.");
  }

  @Test
  public void rejectsWindowsReservedDeviceNames() {
    expectRejected("CON");
    expectRejected("con");
    expectRejected("LPT1");
    expectRejected("com3.backup");
  }

  @Test
  public void allowsNamesThatOnlyContainAReservedNameAsSubstring() {
    FileNameValidator.validate("Contact", "displayName");
    FileNameValidator.validate("CONFIDENTIAL", "displayName");
  }

  @Test
  public void rejectsNamesOverTheSharedByteLimit() {
    expectRejected("a".repeat(256));

    FileNameValidator.validate("a".repeat(255), "displayName");
  }

  @Test
  public void countsMultiByteCharactersTowardTheByteLimitNotTheCharacterLimit() {
    // Each of these is 3 bytes in UTF-8, so 100 of them is already 300 bytes.
    expectRejected("中".repeat(100));
  }
}
