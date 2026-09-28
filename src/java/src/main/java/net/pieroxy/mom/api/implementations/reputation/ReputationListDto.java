package net.pieroxy.mom.api.implementations.reputation;

import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.ReputationListConfig;
import net.pieroxy.mom.detection.reputation.ReputationListType;

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

  public ReputationListDto() {
  }

  public ReputationListDto(ReputationListConfig config) {
    this.id = config.getId();
    this.type = config.getType();
    this.url = config.getUrl();
    this.refreshHours = config.getRefreshHours();
    this.score = config.getScore();
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
