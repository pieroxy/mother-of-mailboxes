package net.pieroxy.mom.api.implementations.applogs;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.utils.logging.AppLogReader;
import net.pieroxy.mom.utils.logging.LoggingBootstrap;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

/** One page of the application log (see {@link AppLogReader}): the current file or an archive, picked by id, never by path. */
@Endpoint(method = ApiMethod.GET)
public class AppLogsApi extends AbstractAuthenticatedEndpoint<AppLogsApiInput, AppLogsApiOutput> {
  private final static int DEFAULT_LIMIT = 100;
  private final static int MAX_LIMIT = 500;

  public AppLogsApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public AppLogsApiOutput processAuthenticated(AppLogsApiInput input) throws IOException {
    List<AppLogReader.LogFile> files = AppLogReader.listFiles(LoggingBootstrap.getLogFile());
    List<AppLogFileDto> fileDtos = files.stream()
        .map(f -> new AppLogFileDto(f.id(), f.file().getName(), f.file().length(), f.file().lastModified()))
        .toList();
    String wanted = input.getFile() == null || input.getFile().isBlank() ? null : input.getFile();
    AppLogReader.LogFile file = files.stream()
        .filter(f -> wanted == null || f.id().equals(wanted))
        .findFirst().orElse(null);
    if (file == null) {
      if (wanted != null) throw new IllegalArgumentException("No such log file: " + wanted);
      return new AppLogsApiOutput(fileDtos, null, List.of(), null);
    }

    Long cursor;
    try {
      cursor = input.getCursor() == null || input.getCursor().isBlank() ? null : Long.parseLong(input.getCursor());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid cursor: " + input.getCursor());
    }
    Level minLevel = input.getMinLevel() == null || input.getMinLevel().isBlank() ? null : Level.parse(input.getMinLevel());
    int limit = input.getLimit() <= 0 ? DEFAULT_LIMIT : Math.min(input.getLimit(), MAX_LIMIT);

    AppLogReader.Page page = AppLogReader.read(file, new AppLogReader.Filter(minLevel, input.getQuery()), cursor, limit);
    List<AppLogEntryDto> entries = page.entries().stream()
        .map(e -> new AppLogEntryDto(e.timestamp(), e.runId(), e.level(), e.source(), e.message(), e.details()))
        .toList();
    return new AppLogsApiOutput(fileDtos, file.id(), entries, page.nextCursor() == null ? null : String.valueOf(page.nextCursor()));
  }
}

@TypeScriptType
class AppLogsApiInput extends AuthenticatedApiInput {
  /** An id from {@code AppLogsApiOutput#files}; null for the first one (the current file). */
  private String file;
  /** A java.util.logging level name (SEVERE, WARNING, INFO, FINE...); null for any. */
  private String minLevel;
  /** Case-insensitive substring of the source, message or details. */
  private String query;
  /** A previous page's {@code nextCursor}; null for the newest entries. */
  private String cursor;
  /** 0: default. */
  private int limit;

  public String getFile() {
    return file;
  }

  public void setFile(String file) {
    this.file = file;
  }

  public String getMinLevel() {
    return minLevel;
  }

  public void setMinLevel(String minLevel) {
    this.minLevel = minLevel;
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public String getCursor() {
    return cursor;
  }

  public void setCursor(String cursor) {
    this.cursor = cursor;
  }

  public int getLimit() {
    return limit;
  }

  public void setLimit(int limit) {
    this.limit = limit;
  }
}

@TypeScriptType
class AppLogsApiOutput {
  /** The current file first, then the archives, most recent first. Empty when MOM doesn't log to a file. */
  private List<AppLogFileDto> files;
  /** The id of the file these entries come from; null when there's none. */
  private String file;
  /** Newest first. */
  private List<AppLogEntryDto> entries;
  /** Null when no older matching entry is left in the file. */
  private String nextCursor;

  public AppLogsApiOutput() {
  }

  public AppLogsApiOutput(List<AppLogFileDto> files, String file, List<AppLogEntryDto> entries, String nextCursor) {
    this.files = files;
    this.file = file;
    this.entries = entries;
    this.nextCursor = nextCursor;
  }

  public List<AppLogFileDto> getFiles() {
    return files;
  }

  public void setFiles(List<AppLogFileDto> files) {
    this.files = files;
  }

  public String getFile() {
    return file;
  }

  public void setFile(String file) {
    this.file = file;
  }

  public List<AppLogEntryDto> getEntries() {
    return entries;
  }

  public void setEntries(List<AppLogEntryDto> entries) {
    this.entries = entries;
  }

  public String getNextCursor() {
    return nextCursor;
  }

  public void setNextCursor(String nextCursor) {
    this.nextCursor = nextCursor;
  }
}

@TypeScriptType
class AppLogFileDto {
  /** "current", or the archive's index (1 = the most recent). */
  private String id;
  private String name;
  private long size;
  /** Epoch milliseconds: for an archive, roughly when its period ended. */
  private long lastModified;

  public AppLogFileDto() {
  }

  public AppLogFileDto(String id, String name, long size, long lastModified) {
    this.id = id;
    this.name = name;
    this.size = size;
    this.lastModified = lastModified;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }

  public long getLastModified() {
    return lastModified;
  }

  public void setLastModified(long lastModified) {
    this.lastModified = lastModified;
  }
}

/** See {@link AppLogReader.Entry}. */
@TypeScriptType
class AppLogEntryDto {
  private String timestamp;
  private String runId;
  private String level;
  private String source;
  private String message;
  private String details;

  public AppLogEntryDto() {
  }

  public AppLogEntryDto(String timestamp, String runId, String level, String source, String message, String details) {
    this.timestamp = timestamp;
    this.runId = runId;
    this.level = level;
    this.source = source;
    this.message = message;
    this.details = details;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  public String getRunId() {
    return runId;
  }

  public void setRunId(String runId) {
    this.runId = runId;
  }

  public String getLevel() {
    return level;
  }

  public void setLevel(String level) {
    this.level = level;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }
}
