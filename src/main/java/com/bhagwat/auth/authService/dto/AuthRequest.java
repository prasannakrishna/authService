package com.bhagwat.auth.authService.dto;

import com.bhagwat.auth.authService.AppUserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthRequest {
    public String getUsername() {
        return username;
    }

    public AppUserRole getAppUserRole(){return appUserRole;}

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEncryptedPassword() {
        return encryptedPassword;
    }

    public void setEncryptedPassword(String encryptedPassword) {
        this.encryptedPassword = encryptedPassword;
    }

    private String username;
    private String password;
    private String encryptedPassword;
    private AppUserRole appUserRole;
}
