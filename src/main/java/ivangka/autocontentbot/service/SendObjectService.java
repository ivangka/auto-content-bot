package ivangka.autocontentbot.service;

import ivangka.autocontentbot.config.ChannelPropertiesConfig;
import ivangka.autocontentbot.model.Channel;
import ivangka.autocontentbot.model.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Service
public class SendObjectService {

    private static final Logger logger = LoggerFactory.getLogger(SendObjectService.class);

    public SendMessage channelsKeyboard(long chatId, ChannelPropertiesConfig channelPropertiesConfig) {
        logger.debug("Creating channels keyboard for chatId: {}", chatId);

        String text = "Выберите канал на котором хотите опубликовать пост";

        List<String> buttonNames = new ArrayList<>();
        for (Channel channel : channelPropertiesConfig.getChannels()) {
            buttonNames.add(channel.getName());
        }
        int rowSize = 2;

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow currentRow = new KeyboardRow();

        for (String buttonName : buttonNames) {
            currentRow.add(buttonName);
            if (currentRow.size() == rowSize) {
                keyboard.add(currentRow);
                currentRow = new KeyboardRow();
            }
        }

        if (!currentRow.isEmpty()) {
            keyboard.add(currentRow);
        }

        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(ReplyKeyboardMarkup.builder()
                        .keyboard(keyboard)
                        .resizeKeyboard(true)
                        .build())
                .build();

        logger.debug("Channels keyboard created successfully for chatId: {}", chatId);
        return sendMessage;
    }

    public SendMessage generatePostCall(long chatId, Channel channel) {
        logger.debug("Generating post call message for chatId: {}, channel: {}", chatId, channel.getName());

        String text = "Сгенерируйте публикацию для канала <b>\"" + channel.getName() +
                "\"</b> с помощью кнопки или введите тему";

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow currentRow = new KeyboardRow();
        currentRow.add("Вернуться к выбору канала");
        currentRow.add("Сгенерировать публикацию");
        keyboard.add(currentRow);

        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(ReplyKeyboardMarkup.builder()
                        .keyboard(keyboard)
                        .resizeKeyboard(true)
                        .build())
                .build();

        logger.debug("Post call message generated successfully for chatId: {}", chatId);
        return sendMessage;
    }

    public SendPhoto post(long chatId, Post post) {
        logger.debug("Creating SendPhoto object for chatId: {}, post title: {}", chatId, post.getTitle());

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow currentRow = new KeyboardRow();
        currentRow.add("Сгенерировать снова");
        currentRow.add("Поменять картинку");
        keyboard.add(currentRow);

        currentRow = new KeyboardRow();
        currentRow.add("Подтвердить");
        keyboard.add(currentRow);

        SendPhoto sendPhoto = SendPhoto.builder()
                .chatId(chatId)
                .photo(new InputFile(post.getImage()))
                .caption(post.getFullText().replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>"))
                .parseMode("HTML")
                .replyMarkup(ReplyKeyboardMarkup.builder()
                        .keyboard(keyboard)
                        .resizeKeyboard(true)
                        .build())
                .build();

        logger.debug("SendPhoto object created successfully for chatId: {}", chatId);
        return sendPhoto;
    }

    public SendMessage areYouSure(long chatId, Post post) {
        logger.debug("Creating confirmation message for chatId: {}, post title: {}", chatId, post.getTitle());

        String text = "Вы уверены, что хотите опубликовать пост <b>\"" + post.getTitle() +
                "\"</b> на канале <b>\"" + post.getChannel().getName() + "\"</b>?";

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow currentRow = new KeyboardRow();
        currentRow.add("Отменить");
        currentRow.add("Опубликовать");
        keyboard.add(currentRow);

        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(ReplyKeyboardMarkup.builder()
                        .keyboard(keyboard)
                        .resizeKeyboard(true)
                        .build())
                .build();

        logger.debug("Confirmation message created successfully for chatId: {}", chatId);
        return sendMessage;
    }

    public SendMessage postPublished(long chatId) {
        logger.debug("Creating post published message for chatId: {}", chatId);
        String text = "<b>Пост успешно опубликован!</b>";
        return deleteKeyBoard(chatId, text);
    }

    public SendMessage deleteKeyBoard(long chatId, String text) {
        logger.debug("Creating message to delete keyboard for chatId: {}", chatId);
        ReplyKeyboardRemove keyboardRemove = new ReplyKeyboardRemove(true);
        return SendMessage
                .builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(keyboardRemove)
                .build();
    }

    public SendMessage defaultSendMessageObject(long chatId, String text) {
        logger.debug("Creating default SendMessage object for chatId: {}", chatId);
        return SendMessage
                .builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .build();
    }

}
