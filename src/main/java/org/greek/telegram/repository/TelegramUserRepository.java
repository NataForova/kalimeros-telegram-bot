package org.greek.telegram.repository;

import org.greek.telegram.model.TelegramUser;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TelegramUserRepository extends MongoRepository<TelegramUser, String> {
    Optional<TelegramUser> findByTelegramUserName(String telegramUserName);
}
