package net.pieroxy.imf.config;

import net.pieroxy.imf.detection.reputation.ReputationListType;

/**
 * An entry of {@code reputationLists} in the global config (see {@link Configuration}): a
 * reputation source to download and refresh periodically, never queried live per message.
 * {@code score} (0=ok, 1=spam) is the value attributed to any IP/domain found in this list;
 * when a matcher references several lists, the score kept is the worst (max) among those that
 * contain the tested value.
 */
public class ReputationListConfig {
  private String id;
  private ReputationListType type;
  private String url;
  private int refreshHours;
  private double score;

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

  /** http(s):// for a remote download, or file:// for a local file. */
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
}
