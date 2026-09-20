package net.pieroxy.imf.utils;

import net.pieroxy.imf.config.credentials.Credential;
import net.pieroxy.imf.config.credentials.CredentialsFile;

import java.util.Map;

/** Looks up a {@code credentials} key against credentials.json's map. */
public class CredentialsResolver {
  /**
   * @param context human-readable description of the caller, used in the error message (e.g.
   *                {@code "mail account \"personal\""}).
   */
  public static Credential resolve(String key, CredentialsFile credentialsFile, String context) {
    if (key == null || key.isBlank()) {
      throw new IllegalStateException(context + " has no \"credentials\" key configured.");
    }
    Map<String, Credential> credentials = credentialsFile.getCredentials();
    if (credentials == null || !credentials.containsKey(key)) {
      throw new IllegalStateException(context + " references credentials key \"" + key + "\", which is missing from credentials.json.");
    }
    return credentials.get(key);
  }
}
