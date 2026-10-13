package com.mirage.console.detection;

import com.mirage.common.AttackerProfile;
import com.mirage.common.Classification;

import java.util.Optional;

/** Strategy: inspect an attacker's events and, on a match, classify them. */
public interface DetectionRule {
    Optional<Classification> classify(AttackerProfile profile);
}
