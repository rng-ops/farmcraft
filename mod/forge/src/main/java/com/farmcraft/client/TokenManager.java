package com.farmcraft.client;

import com.farmcraft.FarmCraft;

/**
 * Client-side manager for work tokens
 */
public class TokenManager {
    
    private static TokenManager instance;
    
    private String currentTokenId;
    private int credits;
    private long expiresAt;
    
    private TokenManager() {}
    
    public static TokenManager getInstance() {
        if (instance == null) {
            instance = new TokenManager();
        }
        return instance;
    }
    
    public void updateToken(String tokenId, int credits, long expiresAt) {
        this.currentTokenId = tokenId;
        this.credits = credits;
        this.expiresAt = expiresAt;
        
        FarmCraft.LOGGER.info("Token updated: {} credits, expires at {}", credits, expiresAt);
    }
    
    public boolean hasValidToken() {
        return currentTokenId != null && 
               credits > 0 && 
               System.currentTimeMillis() < expiresAt;
    }
    
    public String getCurrentTokenId() {
        return currentTokenId;
    }
    
    public int getCredits() {
        return credits;
    }
    
    public long getExpiresAt() {
        return expiresAt;
    }
    
    public long getTimeRemaining() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }
    
    public void consumeCredits(int amount) {
        this.credits = Math.max(0, this.credits - amount);
    }
    
    public void clearToken() {
        this.currentTokenId = null;
        this.credits = 0;
        this.expiresAt = 0;
    }
}
