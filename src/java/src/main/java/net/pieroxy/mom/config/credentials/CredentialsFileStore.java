package net.pieroxy.mom.config.credentials;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

/** Persists credentials.json — shared by {@code Runner}'s startup password-hash migration and {@code ServiceProvider}'s own saves. */
public final class CredentialsFileStore {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private CredentialsFileStore() {}

  public static void save(File path, CredentialsFile credentialsFile) throws IOException {
    try (Writer w = new FileWriter(path)) {
      GSON.toJson(credentialsFile, w);
    }
  }
}
