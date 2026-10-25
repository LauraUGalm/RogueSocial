package com.roguesocial.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.roguesocial.social.FriendService.Relation;
import com.roguesocial.social.Post.Audience;
import com.roguesocial.social.ProfileService.ProfileView;
import com.roguesocial.user.User;
import com.roguesocial.user.UserBank;
import com.roguesocial.user.UserRepository;

/** Set up like {@link PostServiceTest}: the local Mongo's test database, users from a mock. */
@SpringBootTest(properties = "spring.data.mongodb.uri=mongodb://localhost:27017/rogue_social_test")
@EnabledIf("com.roguesocial.social.PostServiceTest#mongoIsRunning")
class ProfileServiceTest {

    @Autowired
    private ProfileService profiles;

    @Autowired
    private FriendService friends;

    @Autowired
    private FollowService follows;

    @Autowired
    private BlockService blocks;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private GridFsTemplate files;

    @MockitoBean
    private UserRepository users;

    @MockitoBean
    private UserBank purse;

    @BeforeEach
    void freshCollections() {
        mongo.remove(new Query(), Profile.class);
        mongo.remove(new Query(), Follow.class);
        mongo.remove(new Query(), Friendship.class);
        mongo.remove(new Query(), Block.class);
        files.delete(new Query());
        for (String name : List.of("ann", "bob", "cat", "dan")) {
            when(users.findByUsername(name)).thenReturn(Optional.of(new User(
                    name.charAt(0), name, name + "@example.com", OffsetDateTime.now(), 1, 5, 5, 20)));
        }
        // ann and bob are friends; cat follows ann; dan is a stranger to all of them.
        friends.request("ann", "bob");
        friends.request("bob", "ann");
        follows.follow("cat", "ann");
    }

    /** A plain picture of the given size, as a PNG upload. */
    private static InputStream png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new ByteArrayInputStream(out.toByteArray());
    }

    private static BufferedImage decode(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    @Test
    void aboutLocationAndStatusAreSavedAndBlankClears() {
        ProfileView saved = profiles.update("ann", "  I fight trolls  ", "Ohio", "On floor 3");

        assertThat(saved.about()).isEqualTo("I fight trolls");
        assertThat(saved.location()).isEqualTo("Ohio");
        assertThat(saved.statusAt()).isNotNull();

        ProfileView later = profiles.update("ann", null, "", "On floor 3");

        assertThat(later.about()).as("null leaves it").isEqualTo("I fight trolls");
        assertThat(later.location()).as("blank clears it").isNull();
        assertThat(later.statusAt()).as("same status, same time").isEqualTo(saved.statusAt());
        assertThatThrownBy(() -> profiles.update("ann", "x".repeat(501), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theProfileSaysWhereTheViewerStands() {
        assertThat(profiles.view("bob", "ann").relation()).isEqualTo(Relation.FRIENDS);
        ProfileView catsView = profiles.view("cat", "ann");
        assertThat(catsView.relation()).isEqualTo(Relation.NONE);
        assertThat(catsView.following()).isTrue();
        assertThat(profiles.view("ann", "cat").followsYou()).isTrue();
        assertThat(profiles.view("ann", "ann").mine()).isTrue();
    }

    @Test
    void theProfilePictureIsASmallSquareForEveryone() throws IOException {
        profiles.setPicture("ann", png(800, 600));

        BufferedImage picture = decode(profiles.picture("dan", "ann").bytes());
        assertThat(picture.getWidth()).isEqualTo(256);
        assertThat(picture.getHeight()).isEqualTo(256);
        assertThat(profiles.view("dan", "ann").pictureId()).isNotNull();
    }

    @Test
    void aNewPictureReplacesTheOld() throws IOException {
        profiles.setPicture("ann", png(300, 300));
        String first = profiles.view("ann", "ann").pictureId();

        profiles.setPicture("ann", png(300, 300));

        assertThat(profiles.view("ann", "ann").pictureId()).isNotEqualTo(first);
        assertThat(mongo.count(new Query(), "fs.files")).isEqualTo(1);
    }

    @Test
    void eachGalleryPictureHasItsOwnAudience() throws IOException {
        String open = profiles.addToGallery("ann", png(2048, 1024), Audience.FOLLOWERS).id();
        String secret = profiles.addToGallery("ann", png(100, 100), Audience.FRIENDS).id();

        assertThat(profiles.view("bob", "ann").gallery()).extracting(ProfileService.GalleryItem::id)
                .containsExactly(secret, open);
        assertThat(profiles.view("cat", "ann").gallery()).extracting(ProfileService.GalleryItem::id)
                .containsExactly(open);
        assertThat(profiles.view("dan", "ann").gallery()).as("followers pictures need a follow").isEmpty();
        assertThatThrownBy(() -> profiles.galleryImage("cat", secret)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> profiles.galleryImage("dan", open)).isInstanceOf(NoSuchElementException.class);

        BufferedImage shrunk = decode(profiles.galleryImage("cat", open).bytes());
        assertThat(shrunk.getWidth()).isEqualTo(1024);
        assertThat(shrunk.getHeight()).isEqualTo(512);
    }

    @Test
    void onlyTheOwnerRemovesFromTheirGallery() throws IOException {
        String id = profiles.addToGallery("ann", png(50, 50), null).id();

        assertThatThrownBy(() -> profiles.removeFromGallery("bob", id)).isInstanceOf(NoSuchElementException.class);
        profiles.removeFromGallery("ann", id);
        assertThat(profiles.view("ann", "ann").gallery()).isEmpty();
    }

    @Test
    void aGalleryHoldsThirty() throws IOException {
        for (int i = 0; i < ProfileService.MAX_GALLERY; i++) {
            profiles.addToGallery("ann", png(10, 10), null);
        }

        assertThatThrownBy(() -> profiles.addToGallery("ann", png(10, 10), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void somethingThatIsntAPictureIsRefused() {
        assertThatThrownBy(() -> profiles.setPicture("ann", new ByteArrayInputStream("hello".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aBlockHidesTheProfileBothWays() throws IOException {
        profiles.setPicture("ann", png(100, 100));
        String open = profiles.addToGallery("ann", png(100, 100), Audience.FOLLOWERS).id();

        blocks.block("cat", "ann");

        assertThatThrownBy(() -> profiles.view("ann", "cat")).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> profiles.view("cat", "ann")).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> profiles.galleryImage("cat", open)).isInstanceOf(NoSuchElementException.class);
    }
}
