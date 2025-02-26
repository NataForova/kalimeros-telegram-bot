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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;


import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
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
                        SendMessage response = new SendMessage(String.valueOf(chatId), "");
                        try {
                            var command = findCommand(text, userName);
                            switch (command) {
                                case START:
                                    response.setText(String.format("Hi %s and yeah! Kalimeros Bot was started!", userName));
                                    break;
                                case ADD_WORD:
                                    response.setText(addWord(userName, text));
                                    break;
                                case FIND_TRANSLATION:
                                    response.setText(findTranslation(userName, text));
                                    break;
                                case START_TRAINING:
                                    response.setText(startTraining(userName));
                                    break;
                                case ANSWER:
                                    response.setText(submitAnswer(userName, text));
                                    break;
                                case STOP_TRAINING:
                                    response.setText(stopTraining(userName));
                                    break;
                                case GET_RANDOM_WORD:
                                    response.setText(getRandomWord(userName));
                                    break;
                                case HELP:
                                    response.setText("Выберите действие:");
                                    response.setReplyMarkup(getHelpKeyboard());
                                    break;
                                default:
                                    response.setText("Unknown or not implemented command");
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
                            response.setText("Error during processing message: "+ e.getMessage() +". Please try again later");
                            sendBotAnswer(response, chatId);
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
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            SendMessage response = new SendMessage(String.valueOf(chatId), "");
            try{
                switch (callbackData) {
                    case "/add":
                        response.setText("Введите слово и его перевод для добавления в словарь.");
                        break;
                    case "/translate":
                        response.setText("Введите слово для перевода.");
                        break;
                    case "/training":
                        response.setText(startTraining(update.getCallbackQuery().getFrom().getUserName()));
                        break;
                    case "/stop":
                        response.setText(stopTraining(update.getCallbackQuery().getFrom().getUserName()));
                        break;
                    case "/random":
                        response.setText(getRandomWord(update.getCallbackQuery().getFrom().getUserName()));
                        break;
                    default:
                        response.setText("Неизвестная команда.");
                        break;
                }
                sendBotAnswer(response, chatId);
            } catch (Exception e) {
                response.setText("Error during processing message: "+ e.getMessage() +". Please try again later");
                sendBotAnswer(response, chatId);
                e.printStackTrace();
                log.error("Error during processing message", e);
            }
        }
    }

    private InlineKeyboardMarkup getHelpKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton addWordButton = new InlineKeyboardButton("➕ Добавить слово");
        addWordButton.setCallbackData("/add");

        InlineKeyboardButton translateButton = new InlineKeyboardButton("🔍 Перевести слово");
        translateButton.setCallbackData("/translate");

        InlineKeyboardButton trainingButton = new InlineKeyboardButton("🎯 Начать тренировку");
        trainingButton.setCallbackData("/training");

        InlineKeyboardButton stopTrainingButton = new InlineKeyboardButton("⏹ Остановить тренировку");
        stopTrainingButton.setCallbackData("/stop");

        InlineKeyboardButton randomWordButton = new InlineKeyboardButton("🎲 Случайное слово");
        randomWordButton.setCallbackData("/random");

        rows.add(List.of(addWordButton, translateButton));
        rows.add(List.of(trainingButton, stopTrainingButton));
        rows.add(List.of(randomWordButton));

        markup.setKeyboard(rows);
        return markup;
    }


    private void sendBotAnswer(SendMessage sendMessage, Long chatId) {
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
            prevCommand = user.get().getPreviousCommand() != null ? user.get().getPreviousCommand() : START;
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
        }  else if (messageText.contains(HELP.getCommand())) {
                return HELP;
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
