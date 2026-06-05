package com.bhagwat.auth.authService;

public enum AppUserRole {
    ADMIN(0, "admin"),
    ASSOCIATE(1, "associate");

    AppUserRole(int code, String role) {
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
