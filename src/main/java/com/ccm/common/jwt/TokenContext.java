package com.ccm.common.jwt;

public class TokenContext {

    private static final ThreadLocal<String> currentToken = new ThreadLocal<>();

    public static void setToken(String token) {
        currentToken.set(token);
    }

    public static String getToken() {
        return currentToken.get();
    }

    public static void clear() {
        currentToken.remove();
    }
}
