package org.greek.telegram;

import lombok.extern.slf4j.Slf4j;
import org.greek.models.DictionaryInput;
import org.greek.telegram.model.TelegramCommand;
import org.greek.telegram.repository.TelegramUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.greek.telegram.model.TelegramCommand.*;

@Slf4j
@Service
public class TelegramService extends TelegramLongPollingBot {

    @Value("${telegram.bot.username}")
    private String botUsername;
    @Value("${telegram.bot.token}")
    private String botToken;
    private final GraphQLClient graphQLClient;
    private final TelegramUserRepository telegramUserRepository;

    public TelegramService(GraphQLClient graphQLClient, TelegramUserRepository telegramUserRepository) {
        this.graphQLClient = graphQLClient;
        this.telegramUserRepository = telegramUserRepository;
    }


    @Override
    public String getBotUsername() {
        // Return the username of your bot
        return botUsername;
    }

    @Override
    public String getBotToken() {
        // Return the token of your bot
        return botToken;
    }

    @Override
    public void onRegister() {
        super.onRegister();
    }


    @Override
    public void onUpdateReceived(Update update) {

        // Check if the message contains text
        if (update.hasMessage()) {
            try {
                var message = update.getMessage();
                if (message.hasText()) {
                    String text = message.getText();
                    var userName = update.getMessage().getChat().getUserName();
                    if (text != null && !text.trim().isEmpty()) {
                        var chatId = message.getChatId();
                        String response = "Received message: " + text;
                        try {
                            var command = findCommand(text, userName);
                            switch (command) {
                                case START:
                                    response = String.format("Hi %s and yeah! Kalimeros Bot was started!", userName);
                                    break;
                                case ADD_WORD:
                                    response = addWord(userName, text);
                                    break;
                                case FIND_TRANSLATION:
                                    response = findTranslation(userName, text);
                                    break;
                                case START_TRAINING:
                                    response = startTraining(userName);
                                    break;
                                case ANSWER:
                                    response = submitAnswer(userName, text);
                                    break;
                                case STOP_TRAINING:
                                    response = stopTraining(userName);
                                    break;
                                case GET_RANDOM_WORD:
                                    response = getRandomWord(userName);
                                    break;
                                case HELP:
                                    response = "Available commands: \n" +
                                            "<b>/add word translation</b> - add word with translation to dictionary, only word as parameter is possible\n" +
                                            "<b>/translate word</b> - find translation for word\n" +
                                            "<b>/training</b> - start daily training based on your word list\n" +
                                            "<b>/stop</b> - stop training\n" +
                                            "<b>/random</b> - get random word for translation\n";
                                    break;
                                default:
                                    response = "Unknown or not implemented command";
                                    break;
                            }
                            var user = telegramUserRepository.findByTelegramUserName(userName);
                            if (user.isPresent()) {
                                var savedUser = user.get();
                                savedUser.setPreviousCommand(command);
                                telegramUserRepository.save(savedUser);
                            }
                            sendBotAnswer(response, chatId);
                        } catch (Exception e) {
                            sendBotAnswer("Error during processing message: "+ e.getMessage() +". Please try again later", chatId);
                            e.printStackTrace();
                            log.error("Error during processing message", e);
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                log.error("Error during processing message", e);
            }
        }
    }

    private void sendBotAnswer(String answer, Long chatId) {
        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), answer);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            log.error("Error during sending message {}", chatId, e);
        }
    }

    private String addWord(String userName, String text) throws Exception {
        List<String> params = Arrays.stream(text.split(" ")).toList();
        if (params.size() < 2) {
            return "Please provide word or word and translation";
        } else if (params.size() == 2) {
            var response = graphQLClient.addWord(new DictionaryInput(params.get(1).trim(),  null), userName).block();
            return "Done\n";
        } else  {
            var response = graphQLClient.addWord(new DictionaryInput(params.get(1).trim(),  params.get(2).trim()), userName).block();
            return "Done\n";
        }
    }

    private String findTranslation(String userName, String text) throws IOException {
        List<String> params = Arrays.stream(text.split(" ")).toList();
        if (params.size() < 2) {
            return "Please provide word for translation";
        } else {
            var wordOrPhrase = text.replace(FIND_TRANSLATION.getCommand(), "").trim();
            var response = graphQLClient.getTranslation(wordOrPhrase, userName).block();
            return  (response != null ? response.getMessage() : "No answer");
        }
    }

    private String startTraining(String userName) throws IOException {
        var response = graphQLClient.startTraining(userName).block();
        return  (response != null ? "Please, write translation of this word "+ response.getWord() + "in replies \n" +
                "Current amount of right answers " + response.getTotal() :
                "No answer");
    }

    private String stopTraining(String userName) throws IOException {
        var response = graphQLClient.stopTraining(userName).block();
        return  (response != null ? "Training stopped" : "No answer");
    }


    private String submitAnswer(String userName, String text) throws IOException {
        if (text.trim().isEmpty()) {
            return "Please provide word for translation";
        } else {
            var response = graphQLClient.submitAnswer(text, userName).block();
            return (response != null ? response.toString() : "No answer");
        }
    }

    private String getRandomWord(String userName) throws IOException {
        var response = graphQLClient.getRandomTranslation(userName).block();
        return (response != null ? response.toString() : "No answer");

    }



    private TelegramCommand findCommand(String messageText, String userName) {
        var user = telegramUserRepository.findByTelegramUserName(userName);
        TelegramCommand prevCommand = START;
        if (user.isPresent()) {
            prevCommand = user.get().getPreviousCommand();
        }
        if (messageText.contains(START.getCommand())) {
            return START;
        } else if (messageText.contains(LOGIN.getCommand())) {
            return LOGIN;
        } else if (messageText.contains(SIGN_UP.getCommand())) {
            return SIGN_UP;
        }  else if (messageText.contains(ADD_WORD.getCommand())) {
            return ADD_WORD;
        } else if (messageText.contains(FIND_TRANSLATION.getCommand())) {
            return FIND_TRANSLATION;
        } else if (messageText.contains(START_TRAINING.getCommand())) {
            return START_TRAINING;
        } else if (messageText.contains(STOP_TRAINING.getCommand())) {
            return STOP_TRAINING;
        } else if (messageText.contains(GET_RANDOM_WORD.getCommand())) {
            return GET_RANDOM_WORD;
        } else if (prevCommand.equals(START_TRAINING) || prevCommand.equals(ANSWER) && !messageText.isEmpty()) {
            return ANSWER;
        }
        return UNKNOWN;
    }

    @Override
    public void onUpdatesReceived(List<Update> updates) {
        super.onUpdatesReceived(updates);
    }
}
