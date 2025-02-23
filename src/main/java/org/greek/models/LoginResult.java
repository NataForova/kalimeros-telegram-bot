package org.greek.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResult {
    private String accessToken;
    private String refreshToken;
    private int expiresIn;
    private String error;
}