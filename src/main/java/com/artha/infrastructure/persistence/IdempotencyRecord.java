package com.artha.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    public enum State { IN_PROGRESS, COMPLETED }

    @Id
    @Column(name = "key_value", length = 255)
    private String keyValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private State state;

    @Column(name = "response")
    private byte[] response;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(String keyValue, State state, byte[] response, Instant expiresAt) {
        this.keyValue = keyValue;
        this.state = state;
        this.response = response;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public String getKeyValue() { return keyValue; }
    public State getState() { return state; }
    public byte[] getResponse() { return response; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public void setState(State state) { this.state = state; }
    public void setResponse(byte[] response) { this.response = response; }
}
