package com.mundia.backend.sports;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * In-memory singleton holding the currently active data source for matches.
 * Survives the lifetime of the JVM. Values: "WC26_IR" | "API_FOOTBALL"
 */
@Component
public class SyncSourceConfig {

    public enum Source { WC26_IR, API_FOOTBALL }

    private final AtomicReference<Source> active = new AtomicReference<>(Source.WC26_IR);

    public Source getActive() { return active.get(); }

    public void setActive(Source s) { active.set(s); }
}
