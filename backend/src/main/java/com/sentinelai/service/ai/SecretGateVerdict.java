package com.sentinelai.service.ai;

/** An AI gate's judgment on one secret candidate: block (or warn) vs clear. */
public record SecretGateVerdict(boolean block, String reason) {
}
