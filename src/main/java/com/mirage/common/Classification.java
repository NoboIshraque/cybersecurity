package com.mirage.common;

/** The verdict of a DetectionRule: a label, its MITRE ATT&CK technique, and a severity. */
public record Classification(String label, String attackTechnique, Severity severity) {
}
