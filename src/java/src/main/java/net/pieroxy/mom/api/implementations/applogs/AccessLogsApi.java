package net.pieroxy.mom.api.implementations.applogs;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.utils.logging.AccessLogReader;
import net.pieroxy.mom.utils.logging.DailyLogFiles;

import java.io.IOException;
import java.util.List;

/** One page of the web server's access log (see {@link AccessLogReader}): one day's file, picked by date, never by path. */
@Endpoint(method = ApiMethod.GET)
public class AccessLogsApi extends AbstractAuthenticatedEndpoint<AccessLogsApiInput, AccessLogsApiOutput> {
  private final static int DEFAULT_LIMIT = 100;
  private final static int MAX_LIMIT = 500;

  public AccessLogsApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public AccessLogsApiOutput processAuthenticated(AccessLogsApiInput input) throws IOException {
    List<DailyLogFiles.DayFile> files = AccessLogReader.listFiles();
    List<AppLogFileDto> fileDtos = files.stream()
        .map(f -> new AppLogFileDto(f.day().toString(), f.file().getName(), f.file().length(), f.file().lastModified()))
        .toList();
    String wanted = input.getFile() == null || input.getFile().isBlank() ? null : input.getFile();
    DailyLogFiles.DayFile file = files.stream()
        .filter(f -> wanted == null || f.day().toString().equals(wanted))
        .findFirst().orElse(null);
    if (file == null) {
      if (wanted != null) throw new IllegalArgumentException("No access log for " + wanted);
      return new AccessLogsApiOutput(fileDtos, null, List.of(), null);
    }

    Long cursor;
    try {
      cursor = input.getCursor() == null || input.getCursor().isBlank() ? null : Long.parseLong(input.getCursor());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid cursor: " + input.getCursor());
    }
    int limit = input.getLimit() <= 0 ? DEFAULT_LIMIT : Math.min(input.getLimit(), MAX_LIMIT);

    AccessLogReader.Page page = AccessLogReader.read(file,
        new AccessLogReader.Filter(input.getMinStatus(), input.getMaxStatus(), input.getQuery()), cursor, limit);
    List<AccessLogEntryDto> entries = page.entries().stream()
        .map(e -> new AccessLogEntryDto(e.raw(), e.remoteAddr(), e.user(), e.time(), e.request(), e.status(), e.bytes(),
            e.referer(), e.userAgent(), e.durationMs()))
        .toList();
    return new AccessLogsApiOutput(fileDtos, file.day().toString(), entries,
        page.nextCursor() == null ? null : String.valueOf(page.nextCursor()));
  }
}

@TypeScriptType
class AccessLogsApiInput extends AuthenticatedApiInput {
  /** A day (yyyy-MM-dd) from {@code AccessLogsApiOutput#files}; null for the most recent one. */
  private String file;
  /** 0: no lower bound. */
  private int minStatus;
  /** 0: no upper bound. */
  private int maxStatus;
  /** Case-insensitive substring of the whole line. */
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

  public int getMinStatus() {
    return minStatus;
  }

  public void setMinStatus(int minStatus) {
    this.minStatus = minStatus;
  }

  public int getMaxStatus() {
    return maxStatus;
  }

  public void setMaxStatus(int maxStatus) {
    this.maxStatus = maxStatus;
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
class AccessLogsApiOutput {
  /** Most recent first; ids are days (yyyy-MM-dd). Empty when MOM doesn't log to a file. */
  private List<AppLogFileDto> files;
  /** The day these entries come from; null when there's none. */
  private String file;
  /** Newest first. */
  private List<AccessLogEntryDto> entries;
  /** Null when no older matching entry is left in the file. */
  private String nextCursor;

  public AccessLogsApiOutput() {
  }

  public AccessLogsApiOutput(List<AppLogFileDto> files, String file, List<AccessLogEntryDto> entries, String nextCursor) {
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

  public List<AccessLogEntryDto> getEntries() {
    return entries;
  }

  public void setEntries(List<AccessLogEntryDto> entries) {
    this.entries = entries;
  }

  public String getNextCursor() {
    return nextCursor;
  }

  public void setNextCursor(String nextCursor) {
    this.nextCursor = nextCursor;
  }
}

/** See {@link AccessLogReader.Entry}: a line that doesn't parse only has {@code raw}, and a 0 status. */
@TypeScriptType
class AccessLogEntryDto {
  private String raw;
  private String remoteAddr;
  private String user;
  private String time;
  private String request;
  private int status;
  private String bytes;
  private String referer;
  private String userAgent;
  private Long durationMs;

  public AccessLogEntryDto() {
  }

  public AccessLogEntryDto(String raw, String remoteAddr, String user, String time, String request, int status, String bytes,
                           String referer, String userAgent, Long durationMs) {
    this.raw = raw;
    this.remoteAddr = remoteAddr;
    this.user = user;
    this.time = time;
    this.request = request;
    this.status = status;
    this.bytes = bytes;
    this.referer = referer;
    this.userAgent = userAgent;
    this.durationMs = durationMs;
  }

  public String getRaw() {
    return raw;
  }

  public void setRaw(String raw) {
    this.raw = raw;
  }

  public String getRemoteAddr() {
    return remoteAddr;
  }

  public void setRemoteAddr(String remoteAddr) {
    this.remoteAddr = remoteAddr;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getTime() {
    return time;
  }

  public void setTime(String time) {
    this.time = time;
  }

  public String getRequest() {
    return request;
  }

  public void setRequest(String request) {
    this.request = request;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public String getBytes() {
    return bytes;
  }

  public void setBytes(String bytes) {
    this.bytes = bytes;
  }

  public String getReferer() {
    return referer;
  }

  public void setReferer(String referer) {
    this.referer = referer;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public Long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(Long durationMs) {
    this.durationMs = durationMs;
  }
}
