package com.mundia.backend.version;

import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/version")
public class VersionController {

    private final BuildProperties buildProperties;

    public VersionController(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping
    public ResponseEntity<VersionResponse> getVersion() {
        return ResponseEntity.ok(new VersionResponse(
            buildProperties.getVersion(),
            buildProperties.get("build.timestamp"),
            buildProperties.get("git.branch"),
            buildProperties.get("git.commit.id")
        ));
    }

    public record VersionResponse(
        String version,
        String buildTime,
        String branch,
        String commit
    ) {
    }
}
