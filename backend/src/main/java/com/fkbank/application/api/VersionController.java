package com.fkbank.application.api;

import org.springframework.boot.info.BuildProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports the running build's own version, so which release answered a request never depends on
 * reading server logs.
 */
@RestController
@RequestMapping("/api")
public class VersionController {

  private final BuildProperties buildProperties;

  VersionController(BuildProperties buildProperties) {
    this.buildProperties = buildProperties;
  }

  @GetMapping(value = "/version", produces = MediaType.APPLICATION_JSON_VALUE)
  public VersionResponse version() {
    return new VersionResponse(buildProperties.getVersion());
  }
}
