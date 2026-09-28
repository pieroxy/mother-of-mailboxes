package net.pieroxy.mom.standalone;

import com.google.gson.Gson;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.credentials.PasswordHasher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileReader;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** {@link Runner#migrateToHashedPasswordIfNeeded}: the startup, plaintext-to-hashed-password migration for the web login. */
public class RunnerTest {
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void hashesAPlaintextPasswordAndClearsItInMemoryAndOnDisk() throws Exception {
    Credential webServerCredential = new Credential();
    webServerCredential.setUsername("admin");
    webServerCredential.setPassword("plaintext-password");
    CredentialsFile credentialsFile = new CredentialsFile();
    credentialsFile.setCredentials(Map.of("web", webServerCredential));
    File credentialsFilePath = new File(tmp.getRoot(), "credentials.json");

    Runner.migrateToHashedPasswordIfNeeded(webServerCredential, credentialsFile, credentialsFilePath);

    assertNull("the plaintext must not survive migration", webServerCredential.getPassword());
    assertTrue(PasswordHasher.verify("plaintext-password", webServerCredential.getPasswordHash()));

    CredentialsFile reloaded = new Gson().fromJson(new FileReader(credentialsFilePath), CredentialsFile.class);
    Credential reloadedCredential = reloaded.getCredentials().get("web");
    assertNull("the plaintext must not be persisted either", reloadedCredential.getPassword());
    assertTrue(PasswordHasher.verify("plaintext-password", reloadedCredential.getPasswordHash()));
  }

  @Test
  public void doesNothingWhenAlreadyHashed() throws Exception {
    Credential webServerCredential = new Credential();
    webServerCredential.setUsername("admin");
    webServerCredential.setPasswordHash(PasswordHasher.hash("already-hashed"));
    CredentialsFile credentialsFile = new CredentialsFile();
    credentialsFile.setCredentials(Map.of("web", webServerCredential));
    // Deliberately a path that can't be written to, to prove no write is even attempted.
    File credentialsFilePath = new File(tmp.getRoot(), "nonexistent-dir/credentials.json");

    Runner.migrateToHashedPasswordIfNeeded(webServerCredential, credentialsFile, credentialsFilePath);

    assertTrue(PasswordHasher.verify("already-hashed", webServerCredential.getPasswordHash()));
  }

  @Test
  public void doesNothingWhenThereIsNoPasswordAtAll() {
    Credential webServerCredential = new Credential();
    webServerCredential.setUsername("admin");
    CredentialsFile credentialsFile = new CredentialsFile();
    credentialsFile.setCredentials(Map.of("web", webServerCredential));
    File credentialsFilePath = new File(tmp.getRoot(), "nonexistent-dir/credentials.json");

    Runner.migrateToHashedPasswordIfNeeded(webServerCredential, credentialsFile, credentialsFilePath);

    assertNull(webServerCredential.getPassword());
    assertEquals(null, webServerCredential.getPasswordHash());
  }
}
