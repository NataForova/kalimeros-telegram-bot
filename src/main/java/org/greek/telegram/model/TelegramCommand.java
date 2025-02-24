package org.greek.telegram.model;

public enum TelegramCommand {
    START("/start"),
    SIGN_UP("/signup"),
    LOGIN("/login"),
    LOG_OUT("/logout"),
    ADD_WORD("/add"),
    FIND_TRANSLATION("/translate"),
    START_TRAINING("/training"),
    ANSWER("/answer"),
    STOP_TRAINING("/stop"),
    GET_RANDOM_WORD("/random"),
    HELP("/help"),
    UNKNOWN("");


    private final String command;

    TelegramCommand(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

}
