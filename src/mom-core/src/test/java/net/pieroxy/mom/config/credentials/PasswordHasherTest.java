package net.pieroxy.mom.config.credentials;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class PasswordHasherTest {
  @Test
  public void verifyAcceptsTheCorrectPassword() {
    PasswordHash hash = PasswordHasher.hash("correct-horse-battery-staple");

    assertTrue(PasswordHasher.verify("correct-horse-battery-staple", hash));
  }

  @Test
  public void verifyRejectsAWrongPassword() {
    PasswordHash hash = PasswordHasher.hash("correct-horse-battery-staple");

    assertFalse(PasswordHasher.verify("wrong-password", hash));
  }

  @Test
  public void verifyRejectsANullPasswordOrHash() {
    PasswordHash hash = PasswordHasher.hash("correct-horse-battery-staple");

    assertFalse(PasswordHasher.verify(null, hash));
    assertFalse(PasswordHasher.verify("correct-horse-battery-staple", null));
  }

  @Test
  public void hashingTheSamePasswordTwiceProducesDifferentSaltsAndHashes() {
    PasswordHash first = PasswordHasher.hash("same-password");
    PasswordHash second = PasswordHasher.hash("same-password");

    assertNotEquals("a fresh random salt every time defeats rainbow tables", first.getSalt(), second.getSalt());
    assertNotEquals(first.getHash(), second.getHash());
    // Both must still independently verify the same password.
    assertTrue(PasswordHasher.verify("same-password", first));
    assertTrue(PasswordHasher.verify("same-password", second));
  }

  @Test
  public void verifyRejectsATamperedHash() {
    PasswordHash hash = PasswordHasher.hash("correct-horse-battery-staple");
    hash.setHash(PasswordHasher.hash("something-else").getHash());

    assertFalse(PasswordHasher.verify("correct-horse-battery-staple", hash));
  }
}
