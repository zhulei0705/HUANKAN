package cn.huankan;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

/** A research sidecar can disable all background writes without changing the live collector. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name="huankan.scheduling-enabled",havingValue="true",matchIfMissing=true)
public class SchedulingConfiguration {}
