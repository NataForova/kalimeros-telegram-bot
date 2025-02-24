package org.greek.telegram.service;

import org.greek.telegram.model.TelegramUser;
import org.greek.telegram.repository.TelegramUserRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

import static org.greek.telegram.model.TelegramCommand.START;

@Service
public class TelegramUserService {
    private static final int MAX_LENGTH_OF_PASS = 8;
    private final TelegramUserRepository telegramUserRepository;

    public TelegramUserService(TelegramUserRepository telegramUserRepository) {
        this.telegramUserRepository = telegramUserRepository;
    }

    public TelegramUser findUser(String telegramUserName) {
        var optUser = telegramUserRepository.findByTelegramUserName(telegramUserName);

        if (optUser.isPresent()) {
            var savedUser = optUser.get();
            savedUser.setPassword(decryptPassword(savedUser.getPassword()));
            return savedUser;
        } else {
            var newUser = new TelegramUser();
            newUser.setTelegramUserName(telegramUserName);
            newUser.setPassword(encryptPassword(generateRandomPassword(MAX_LENGTH_OF_PASS)));
            newUser.setGeneratedEmail(telegramUserName.replace("@", "") + "@kbot.com");
            newUser.setPreviousCommand(START);

            var user =  telegramUserRepository.save(newUser);
            user.setPassword(decryptPassword(user.getPassword()));
            return user;
        }
    }
    private String generateRandomPassword(int length) {
        var chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+";
        var random = new SecureRandom();
        var password = new StringBuilder();

        for (int i = 0; i < length; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }

        return password.toString();
    }

    private String encryptPassword(String password) {
        // Implement your encryption logic here
        return Base64.getEncoder().encodeToString(password.getBytes()); // Replace with actual encryption logic
    }

    private String decryptPassword(String encryptedPassword) {
        return new String(Base64.getDecoder().decode(encryptedPassword)); // Replace with actual decryption logic
    }

}
