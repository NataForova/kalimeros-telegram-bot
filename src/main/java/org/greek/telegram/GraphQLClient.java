package org.greek.telegram;
import org.greek.kalimeros.api.types.SubmitAnswerResult;
import org.greek.kalimeros.api.types.TrainingSession;
import org.greek.telegram.service.TelegramUserService;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.greek.models.DictionaryInput;
import org.greek.models.LoginInput;
import org.greek.models.LoginResult;
import org.greek.models.SuccessResponse;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.cache.Cache;
import javax.cache.CacheManager;
import java.io.IOException;

@Service
@Slf4j
public class GraphQLClient {
    private final String serverURL;
    private final Cache<String, String> cacheUserToken;
    private final TelegramUserService userService;


    public GraphQLClient(JCacheCacheManager cacheManager, TelegramUserService userService,  @Value("${telegram.server-url}") String serverUrl) {
        this.serverURL = serverUrl;
        this.userService = userService;
        CacheManager manager = cacheManager.getCacheManager();
        this.cacheUserToken = manager.getCache("usersToken", String.class, String.class);
    }


    private void hello(String userName) throws IOException {
        //language=GraphQL
        String document = """
            query { 
                hello 
            }
            """;
        var client = createWebClient(userName, true);
        String response = client.document(document)
                .retrieve("hello")
                .toString();
    }

    public Mono<SuccessResponse> addWord(DictionaryInput newWord, String userName) throws IOException {
        //language=GraphQL
        String document = """
        mutation($newWord: DictionaryInput!) {
            addWord(newWord: $newWord) {
                ... on SuccessResponse {
                    message
                }
                ... on ErrorResponse {
                    error
                }
            }
        }
        """;
        var client = createWebClient(userName, true);
        return client.document(document)
                .variable("newWord", newWord)
                .retrieve("addWord")
                .toEntity(SuccessResponse.class)
                .doOnError(e -> log.error("Error while adding word", e));
    }

    private Mono<LoginResult> signUp(LoginInput credentials) throws IOException {
        //language=GraphQL
        String document = """
        mutation($credentials: LoginInput!) {
            signUp(credentials: $credentials) {
                ... on AuthPayload {
                    accessToken
                    refreshToken
                    expiresIn
                }
                ... on ErrorResponse {
                    error
                }
            }
        }
        """;
        var client = createWebClient("", false);
        return client.document(document)
                .variable("credentials", credentials)
                .retrieve("signUp")
                .toEntity(LoginResult.class)
                .doOnError(e -> log.error("Error during sign up", e));
    }

    private Mono<LoginResult> signIn(LoginInput credentials) throws IOException {
        //language=GraphQL
        String document = """
        mutation($credentials: LoginInput!) {
            signIn(credentials: $credentials) {
                ... on AuthPayload {
                    accessToken
                    refreshToken
                    expiresIn
                }
                ... on ErrorResponse {
                    error
                }
            }
        }
        """;
        var client = createWebClient("", false);
        return client.document(document)
                .variable("credentials", credentials)
                .retrieve("signIn")
                .toEntity(LoginResult.class)
                .doOnError(e -> log.error("Error during sign in", e));
    }

    public Mono<TrainingSession> startTraining(String userName) throws IOException {
        //language=GraphQL
        String document = """
    query {
        startTraining {
            ... on TrainingSession {
                word
                completed
                total
            }
            ... on ErrorResponse {
                error
            }
            ... on SuccessResponse {
                message
            }
        }
    }
    """;
        var client = createWebClient(userName, true);
        return client.document(document)
                .retrieve("startTraining")
                .toEntity(TrainingSession.class)
                .doOnError(e -> log.error("Error during start training", e));
    }

    public Mono<SubmitAnswerResult> submitAnswer(String answer, String userName) throws IOException {
        //language=GraphQL
        String document = """
    mutation($answer: String!) {
        submitAnswer(answer: $answer) {
            ... on TrainingSession {
                word
                completed
                total
            }
            ... on ErrorResponse {
                error
            }
            ... on SuccessResponse {
                message
            }
        }
    }
    """;
        var client = createWebClient(userName, true);
        return client.document(document)
                .variable("answer", answer)
                .retrieve("submitAnswer")
                .toEntity(SubmitAnswerResult.class)
                .doOnError(e -> log.error("Error during submit answer", e));
    }
    public Mono<SuccessResponse> stopTraining(String userName) throws IOException {
        //language=GraphQL
        String document = """
    mutation {
        stopTraining {
            ... on SuccessResponse {
                message
            }
            ... on ErrorResponse {
                error
            }
        }
    }
    """;
        var client = createWebClient(userName, true);
        return client.document(document)
                .retrieve("stopTraining")
                .toEntity(SuccessResponse.class)
                .doOnError(e -> log.error("Error during stop training", e));
    }


    public Mono<SuccessResponse> getTranslation(String word, String userName) throws IOException {
        //language=GraphQL
        String document = """
    query($word: String!) {
        getTranslation(word: $word) {
            ... on SuccessResponse {
                message
            }
            ... on ErrorResponse {
                error
            }
        }
    }
    """;
        var client = createWebClient(userName, true);
        return client.document(document)
                .variable("word", word)
                .retrieve("getTranslation")
                .toEntity(SuccessResponse.class)
                .doOnError(e -> log.error("Error during get translation", e));
    }

    public Mono<String> getRandomTranslation(String userName) throws IOException {
        //language=GraphQL
        String document = """
    query {
        getRandomTranslation
    }
    """;
        var client = createWebClient(userName, true);
        return client.document(document)
                .retrieve("getRandomTranslation")
                .toEntity(String.class)
                .doOnError(e -> log.error("Error during get random translation", e));
    }



    private HttpGraphQlClient createWebClient(String userName, boolean isNeedToken) throws IOException {
        if (isNeedToken) {
            var token = getTokenForUser(userName);
            WebClient webClient = WebClient.builder()
                    .baseUrl(serverURL)
                    .defaultHeader("Authorization", "Bearer " + token)
                    .build();
            return HttpGraphQlClient.builder(webClient).build();
        } else {
            WebClient webClient = WebClient.builder()
                    .baseUrl(serverURL)
                    .build();
            return HttpGraphQlClient.builder(webClient).build();
        }
    }

    private String getTokenForUser(String userName) throws IOException {
        var token = cacheUserToken.get(userName);
        if  (token != null) {
            return token;
        } else {
            var user = userService.findUser(userName);
            //пробуем залогиниться
            var loginResult = signIn(new LoginInput(user.getGeneratedEmail(), user.getPassword())).block();
            if (loginResult != null && loginResult.getError() == null && loginResult.getAccessToken() != null) {
                cacheUserToken.put(userName, loginResult.getAccessToken());
                return loginResult.getAccessToken();
            } else {
                var singUpResult = signUp(new LoginInput(user.getGeneratedEmail(), user.getPassword())).block();
                if (singUpResult != null && singUpResult.getError() == null) {
                    cacheUserToken.put(userName, singUpResult.getAccessToken());
                    return singUpResult.getAccessToken();
                } else if (singUpResult != null && singUpResult.getError() != null){
                    throw new IOException("Can't login or sign up user " + userName + " error: " + singUpResult.getError());
                } else {
                    throw new IOException("Can't login or sign up user " + userName);
                }

            }
        }
    }
}
