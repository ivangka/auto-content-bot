package ivangka.autocontentbot.service;

import ivangka.autocontentbot.model.Channel;
import ivangka.autocontentbot.model.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.File;
import java.io.IOException;

@Service
public class PostService {

    private static final Logger logger = LoggerFactory.getLogger(PostService.class);

    private final TextGenerationService textGenerationService;
    private final ImageGenerationService imageGenerationService;

    @Autowired
    public PostService(TextGenerationService textGenerationService,
                       ImageGenerationService imageGenerationService) {
        this.textGenerationService = textGenerationService;
        this.imageGenerationService = imageGenerationService;
    }

    public Post generatePost(Channel channel, String selectedTopic, long userId) {
        logger.debug("[User ID: {}] Generating post for channel: {}, topic: {}, userId: {}", userId, channel.getName(),
                selectedTopic, userId);

        Post post = new Post();
        post.setChannel(channel);
        if (selectedTopic != null)
            post.setSelectedTopic(selectedTopic);

        // full text of the post
        post.setFullText(textGenerationService.generateFullText(channel, selectedTopic));

        // if llm response is not expected
        if (!checkPostTextCorrectness(post)) {
            logger.warn("[User ID: {}] Generated post is not valid", userId);
            return null;
        }

        post.setTitle(extractTitle(post.getFullText()));

        // image of the post
        try {
            File image = imageGenerationService.generateImage(userId, post.getTitle());
            if (image == null) {
                logger.warn("[User ID: {}] Failed to generate image for the post", userId);
                return null;
            } else {
                post.setImage(image);
            }
        } catch (IOException | InterruptedException e) {
            logger.error("[User ID: {}] Exception while generating image for the post", userId, e);
            return null;
        }

        logger.debug("[User ID: {}] Post generated for channel: {}", userId, channel.getName());
        return post;
    }

    public Post editGeneratedPost(Channel channel, Post post, String edits) {
        logger.debug("Editing generated post for channel: {}, edits: {}", channel.getName(), edits);
        String fullText = textGenerationService.editFullText(post, edits);
        if (fullText == null) {
            return null;
        } else {
            post.setFullText(fullText);
            post.setTitle(extractTitle(post.getFullText()));
            logger.debug("Post edited successfully");
            return post;
        }
    }

    private String extractTitle(String text) {
        String[] lines = text.split("\n");
        return lines[0].replace("**", "");
    }

    public SendPhoto publishPost(long channelId, Post post) {
        logger.debug("Publishing post to channelId: {}", channelId);

        SendPhoto sendPhoto = SendPhoto
                .builder()
                .chatId(channelId)
                .photo(new InputFile(post.getImage()))
                .caption(post.getFullText().replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>"))
                .parseMode("HTML")
                .build();

        logger.debug("Post published successfully to channelId: {}", channelId);
        return sendPhoto;
    }

    public boolean checkPostTextCorrectness(Post post) {
        if (post.getFullText() == null) return false;
        String[] lines = post.getFullText().split("\n");
        return lines.length > 1;
    }

}
