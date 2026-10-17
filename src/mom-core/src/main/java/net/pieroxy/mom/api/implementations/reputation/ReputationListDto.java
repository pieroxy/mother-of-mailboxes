package net.pieroxy.mom.api.implementations.reputation;

import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.ReputationListConfig;
import net.pieroxy.mom.detection.reputation.ReputationListType;
import net.pieroxy.mom.detection.reputation.ReputationRegistry;

import java.time.Instant;

/**
 * Public: reused from {@code generalsettings} (a different package) to both display and save the
 * full list, not just the id+type the rule editor's dropdown needs.
 */
@TypeScriptType
public class ReputationListDto {
  private String id;
  private ReputationListType type;
  private String url;
  private int refreshHours;
  private double score;
  /** ISO-8601 timestamp the currently loaded cache was last written, or null if none exists yet. */
  private String lastRefreshTimestamp;
  private int itemCount;
  private long contentSizeBytes;

  public ReputationListDto() {
  }

  public ReputationListDto(ReputationListConfig config) {
    this.id = config.getId();
    this.type = config.getType();
    this.url = config.getUrl();
    this.refreshHours = config.getRefreshHours();
    this.score = config.getScore();
  }

  /** For the homepage dashboard (see {@code ReputationListsApi}): also carries live registry state. */
  public ReputationListDto(ReputationListConfig config, ReputationRegistry registry) {
    this(config);
    long lastModified = registry.getLastModified(config.getId());
    this.lastRefreshTimestamp = lastModified > 0 ? Instant.ofEpochMilli(lastModified).toString() : null;
    this.itemCount = registry.getItemCount(config.getId());
    this.contentSizeBytes = registry.getContentSizeBytes(config.getId());
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public ReputationListType getType() {
    return type;
  }

  public void setType(ReputationListType type) {
    this.type = type;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public int getRefreshHours() {
    return refreshHours;
  }

  public void setRefreshHours(int refreshHours) {
    this.refreshHours = refreshHours;
  }

  public double getScore() {
    return score;
  }

  public void setScore(double score) {
    this.score = score;
  }

  public String getLastRefreshTimestamp() {
    return lastRefreshTimestamp;
  }

  public void setLastRefreshTimestamp(String lastRefreshTimestamp) {
    this.lastRefreshTimestamp = lastRefreshTimestamp;
  }

  public int getItemCount() {
    return itemCount;
  }

  public void setItemCount(int itemCount) {
    this.itemCount = itemCount;
  }

  public long getContentSizeBytes() {
    return contentSizeBytes;
  }

  public void setContentSizeBytes(long contentSizeBytes) {
    this.contentSizeBytes = contentSizeBytes;
  }

  /** Converts this DTO back into the shape config.json/ReputationRegistry use — see UpdateGeneralSettingsApi. */
  public ReputationListConfig toConfig() {
    ReputationListConfig config = new ReputationListConfig();
    config.setId(id);
    config.setType(type);
    config.setUrl(url);
    config.setRefreshHours(refreshHours);
    config.setScore(score);
    return config;
  }
}
