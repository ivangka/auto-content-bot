package ivangka.autocontentbot.controller;

import ivangka.autocontentbot.config.ChannelPropertiesConfig;
import ivangka.autocontentbot.config.SecurityConfig;
import ivangka.autocontentbot.model.Post;
import ivangka.autocontentbot.model.UserState;
import ivangka.autocontentbot.service.ImageGenerationService;
import ivangka.autocontentbot.service.PostService;
import ivangka.autocontentbot.service.SendObjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class TelegramBotController implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger logger = LoggerFactory.getLogger(TelegramBotController.class);

    private final TelegramClient telegramClient;
    private final SendObjectService sendObjectService;
    private final ChannelPropertiesConfig channelPropertiesConfig;
    private final SecurityConfig securityConfig;
    private final PostService postService;
    private final ImageGenerationService imageGenerationService;
    private final Map<Long, UserState> userStates;

    @Autowired
    public TelegramBotController(TelegramClient telegramClient,
                                 SendObjectService sendObjectService,
                                 ChannelPropertiesConfig channelPropertiesConfig,
                                 SecurityConfig securityConfig,
                                 PostService postService,
                                 ConcurrentHashMap<Long, UserState> userStates,
                                 ImageGenerationService imageGenerationService) {

        this.telegramClient = telegramClient;
        this.sendObjectService = sendObjectService;
        this.channelPropertiesConfig = channelPropertiesConfig;
        this.securityConfig = securityConfig;
        this.postService = postService;
        this.userStates = userStates;
        this.imageGenerationService = imageGenerationService;

    }

    @Override
    public void consume(Update update) {

        if (update.hasMessage()) {
            logger.debug("[User ID: {}] Processing message from user: {}", update.getMessage().getFrom().getId(),
                    update.getMessage().getFrom().getId());

            // if message has not text
            if (!update.getMessage().hasText()) {
                logger.warn("[User ID: {}] Received a message without text. Chat ID: {}",
                        update.getMessage().getFrom().getId(),
                        update.getMessage().getChatId());
                execute(sendObjectService.defaultSendMessageObject(update.getMessage().getChatId(),
                        "Я работаю только с текстовыми сообщениями"));
                return;
            }

            // inits
            User user = update.getMessage().getFrom();
            long userId = user.getId();
            String messageFromUserText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            SendMessage sendMessage = null;
            SendPhoto sendPhoto = null;
            UserState userState = userStates.getOrDefault(userId, new UserState(userId));


            userStates.put(userId, userState);
            logger.debug("[User ID: {}] User state for user {}: {}", userState.getId(), userId, userState);

            // access check
            if (!securityConfig.isUserAllowed(userId)) {
                logger.warn("[User ID: {}] User {} is not allowed to access the resource", userState.getId(), userId);
                sendMessage = sendObjectService.defaultSendMessageObject(chatId,
                        "Кажется, у вас нет разрешения на доступ к этому ресурсу");
                execute(sendMessage);
                return;
            }

            switch (messageFromUserText) {

                // new post (start)
                case "/start", "Вернуться к выбору канала":
                    logger.debug("[User ID: {}] User {} started a new session", userState.getId(), userId);
                    helloMessageExecute(sendMessage, chatId, userState);
                    break;

                // reset fields and delete keyboard
                case "/reset":
                    logger.debug("[User ID: {}] User {} reset their session", userState.getId(), userId);
                    userState.resetFields();
                    sendMessage = sendObjectService.deleteKeyBoard(chatId,
                            "<b>Все действия успешно сброшены</b>");
                    execute(sendMessage);
                    break;

                case "Сгенерировать публикацию", "Сгенерировать снова": // "generate publication" / "generate again"
                    logger.debug("[User ID: {}] User {} requested to generate a publication",
                            userState.getId(), userId);
                    if (userState.getSelectedChannel() == null) {
                        somethingWrongExecute(sendMessage, chatId, userState);
                    } else {
                        generatePostExecute(sendMessage, sendPhoto, chatId, userState.getSelectedTopic(),
                                userState, userId);
                    }
                    break;

                case "Поменять картинку": // "change image"
                    logger.debug("[User ID: {}] User {} requested to change the image", userState.getId(), userId);
                    if (userState.getSelectedChannel() == null || userState.getGeneratedPost() == null) {
                        somethingWrongExecute(sendMessage, chatId, userState);
                    } else {
                        changeImageExecute(sendMessage, sendPhoto, chatId, userId, userState);
                    }
                    break;

                case "Подтвердить": // "confirm"
                    logger.debug("[User ID: {}] User {} confirmed the publication", userState.getId(), userId);
                    if (userState.getSelectedChannel() == null || userState.getGeneratedPost() == null) {
                        somethingWrongExecute(sendMessage, chatId, userState);
                    } else {
                        sendMessage = sendObjectService.areYouSure(chatId, userState.getGeneratedPost());
                        execute(sendMessage);
                    }
                    break;

                case "Отменить": // "cancel"
                    logger.debug("[User ID: {}] User {} canceled the publication", userState.getId(), userId);
                    if (userState.getSelectedChannel() == null || userState.getGeneratedPost() == null) {
                        somethingWrongExecute(sendMessage, chatId, userState);
                    } else {
                        userState.resetFields();
                        sendMessage = sendObjectService.deleteKeyBoard(chatId, "<b>Публикация отменена</b>");
                        execute(sendMessage);
                    }
                    break;

                case "Опубликовать": // publish
                    logger.debug("[User ID: {}] User {} published the post", userState.getId(), userId);
                    if (userState.getSelectedChannel() == null || userState.getGeneratedPost() == null) {
                        somethingWrongExecute(sendMessage, chatId, userState);
                    } else {
                        sendPhoto = postService.publishPost(userState.getSelectedChannel().getId(),
                                userState.getGeneratedPost());
                        execute(sendPhoto);

                        sendMessage = sendObjectService.postPublished(chatId);
                        execute(sendMessage);

                        userState.resetFields();
                    }
                    break;

                default:
                    // if user just selected channel
                    if (channelPropertiesConfig.containsChannelName(messageFromUserText)) {
                        logger.debug("[User ID: {}] User {} selected a channel: {}", userState.getId(),
                                userId, messageFromUserText);

                        if (userState.getSelectedChannel() != null || userState.getGeneratedPost() != null) {
                            somethingWrongExecute(sendMessage, chatId, userState);
                        } else {
                            userState.setSelectedChannel(channelPropertiesConfig.getChannelByName(messageFromUserText));
                            sendMessage = sendObjectService.generatePostCall(chatId, userState.getSelectedChannel());
                            execute(sendMessage);
                        }

                    } else { // user wrote own topic or want edit generated post
                        logger.debug("[User ID: {}] User {} wrote a custom topic or wants to edit a post",
                                userState.getId(), userId);

                        if (userState.getSelectedChannel() == null) { // didn't select channel warning
                            somethingWrongExecute(sendMessage, chatId, userState);
                        } else if (userState.getGeneratedPost() != null) { // user want edit generated post
                            editGeneratedPostExecute(sendMessage, sendPhoto, chatId, messageFromUserText,
                                    userState, userState.getGeneratedPost());
                        } else { // user wrote own topic
                            userState.setSelectedTopic(messageFromUserText);
                            generatePostExecute(sendMessage, sendPhoto, chatId, userState.getSelectedTopic(),
                                    userState, userId);
                        }

                    }
                    break;

            }

        }

    }

    private void helloMessageExecute(SendMessage sendMessage, long chatId, UserState userState) {
        logger.debug("[User ID: {}] Executing helloMessageExecute for chat ID: {}", userState.getId(), chatId);
        userState.resetFields();
        sendMessage = sendObjectService.channelsKeyboard(chatId, channelPropertiesConfig);
        execute(sendMessage);
        logger.info("[User ID: {}] Sent channel selection keyboard to chat ID: {}", userState.getId(), chatId);
    }

    private void somethingWrongExecute(SendMessage sendMessage, long chatId, UserState userState) {
        logger.warn("[User ID: {}] Something went wrong for chat ID: {}", userState.getId(), chatId);
        sendMessage = sendObjectService.defaultSendMessageObject(chatId,
                "Упс, похоже что-то не так... Давайте начнем сначала");
        execute(sendMessage);
        helloMessageExecute(sendMessage, chatId, userState);
    }

    protected void generatePostExecute(SendMessage sendMessage, SendPhoto sendPhoto, long chatId, String topic,
                                     UserState userState, long userId) {
        logger.info("[User ID: {}] Generating post for user ID: {} with topic: {}", userState.getId(), userId, topic);
        sendMessage = sendObjectService.defaultSendMessageObject(chatId,
                "Пост генерируется, это может занять до двух минут. Пожалуйста, подождите...");
        execute(sendMessage);
        Post generatedPost = postService.generatePost(userState.getSelectedChannel(), topic, userId);
        checkingCorrectnessPost(sendMessage, sendPhoto, chatId, userState, generatedPost);
    }

    protected void editGeneratedPostExecute(SendMessage sendMessage, SendPhoto sendPhoto, long chatId, String edits,
                                          UserState userState, Post post) {
        logger.info("[User ID: {}] Editing generated post for chat ID: {}", userState.getId(), chatId);
        sendMessage = sendObjectService.defaultSendMessageObject(chatId, "Редактирование...");
        execute(sendMessage);
        Post editedPost = postService.editGeneratedPost(userState.getSelectedChannel(), post, edits);
        checkingCorrectnessPost(sendMessage, sendPhoto, chatId, userState, editedPost);
    }

    protected void changeImageExecute(SendMessage sendMessage, SendPhoto sendPhoto,
                                    long chatId, long userId, UserState userState) {
        logger.info("[User ID: {}] Changing image for user ID: {}", userState.getId(), userId);

        sendMessage = sendObjectService.defaultSendMessageObject(chatId, "Редактирование...");
        execute(sendMessage);

        String userPrompt = userState.getGeneratedPost().getTitle();

        File changedImage = null;
        try {
            changedImage = imageGenerationService.generateImage(userId, userPrompt);
        } catch (IOException | InterruptedException e) {
            logger.error("[User ID: {}] Error while generating image for user {}: {}", userState.getId(), userId,
                    e.getMessage(), e);
        }
        checkingCorrectnessPost(sendMessage, sendPhoto, chatId, userState, changedImage);

    }

    private void checkingCorrectnessPost(SendMessage sendMessage, SendPhoto sendPhoto, long chatId,
                                         UserState userState, Post generatedPost) {
        if (generatedPost == null || generatedPost.getImage() == null) {
            logger.warn("[User ID: {}] Generated post or image is null for chat ID: {}", userState.getId(), chatId);
            sendMessage = sendObjectService.defaultSendMessageObject(chatId,
                    "Ошибка при обработке запроса, попробуйте снова");
            execute(sendMessage);
        } else {
            logger.debug("[User ID: {}] Post is successful for chat ID: {}", userState.getId(), chatId);
            userState.setGeneratedPost(generatedPost);
            postExecute(sendMessage, sendPhoto, chatId, userState);
        }
    }

    private void checkingCorrectnessPost(SendMessage sendMessage, SendPhoto sendPhoto, long chatId,
                                         UserState userState, File changedImage) {
        if (changedImage == null) {
            logger.warn("[User ID: {}] Image is null for chat ID: {}", userState.getId(), chatId);
            sendMessage = sendObjectService.defaultSendMessageObject(chatId,
                    "К сожалению, что-то пошло не так, попробуйте снова");
            execute(sendMessage);
        } else {
            logger.debug("[User ID: {}] Post is successful for chat ID: {}", userState.getId(), chatId);
            userState.getGeneratedPost().setImage(changedImage);
            postExecute(sendMessage, sendPhoto, chatId, userState);
        }
    }

    private void postExecute(SendMessage sendMessage, SendPhoto sendPhoto, long chatId, UserState userState) {
        sendPhoto = sendObjectService.post(chatId, userState.getGeneratedPost());
        execute(sendPhoto);
        logger.info("[User ID: {}] Sent generated post to chat ID: {}", userState.getId(), chatId);
        sendMessage = sendObjectService.defaultSendMessageObject(chatId,
                "Введите правки, если хотите отредактировать текст");
        execute(sendMessage);
    }

    // execute text message
    private void execute(SendMessage sendMessage) {
        try {
            telegramClient.execute(sendMessage);
            logger.debug("Message sent successfully: {}", sendMessage.getText());
        } catch (TelegramApiException e) {
            logger.error("Telegram API error: {}", e.getMessage(), e);
        }
    }

    // execute message with photo
    private void execute(SendPhoto sendPhoto) {
        try {
            telegramClient.execute(sendPhoto);
            logger.debug("Message with image ({}) sent successfully: {}", sendPhoto.getPhoto().getMediaName(),
                    sendPhoto.getCaption());
        } catch (TelegramApiException e) {
            logger.error("Telegram API error: {}", e.getMessage(), e);
        }
    }

}
