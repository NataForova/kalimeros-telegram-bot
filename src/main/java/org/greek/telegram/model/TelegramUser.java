package org.greek.telegram.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "telegram_user")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TelegramUser {
    @Id
    public String id;
    public String telegramUserName;
    public String password;
    public String generatedEmail;
    public TelegramCommand previousCommand;

}
