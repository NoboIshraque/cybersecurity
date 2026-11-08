package com.mirage.sensor;

/** Decides, per incoming connection, how a decoy should treat the source. */
public interface ConnectionGate {

    enum Decision { ALLOW, TARPIT, FEED_FAKE, BLOCK }

    Decision decide(String sourceIp);

    ConnectionGate ALLOW_ALL = sourceIp -> Decision.ALLOW;
}
