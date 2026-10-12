package net.pieroxy.mom.config.general;

import net.pieroxy.mom.api.metadata.TypeScriptNonConstEnum;
import net.pieroxy.mom.api.metadata.TypeScriptType;

/** How the SMTP connection is encrypted. Plain, unencrypted SMTP isn't supported. */
@TypeScriptType
@TypeScriptNonConstEnum
public enum SmtpSecurity {
  /** TLS from the first byte (SMTPS), usually port 465. */
  TLS,
  /** Plain connection upgraded with STARTTLS before authenticating, usually port 587. Refused if the server doesn't offer it. */
  STARTTLS
}
