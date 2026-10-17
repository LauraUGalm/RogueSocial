package com.roguesocial.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.roguesocial.user.User;
import com.roguesocial.user.UserBank;
import com.roguesocial.user.UserRepository;

/**
 * Runs against the local Mongo, in its own database {@code rogue_social_test}, and is skipped
 * when Mongo isn't running. Users and gold come from mocks, so Postgres isn't needed.
 */
@SpringBootTest(properties = "spring.data.mongodb.uri=mongodb://localhost:27017/rogue_social_test")
@EnabledIf("mongoIsRunning")
class PostServiceTest {

    static boolean mongoIsRunning() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 27017), 300);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Autowired
    private PostService posts;

    @Autowired
    private FollowService follows;

    @Autowired
    private MongoTemplate mongo;

    @MockitoBean
    private UserRepository users;

    @MockitoBean
    private UserBank purse;

    @BeforeEach
    void freshCollections() {
        // Remove the documents but keep the collections, and so their indexes.
        mongo.remove(new Query(), Post.class);
        mongo.remove(new Query(), Follow.class);
        mongo.remove(new Query(), Like.class);
        for (String name : List.of("ann", "bob", "cat")) {
            when(users.findByUsername(name)).thenReturn(Optional.of(new User(
                    name.charAt(0), name, name + "@example.com", OffsetDateTime.now(), 1, 5, 5, 20)));
        }
    }

    @Test
    void postingCostsOneGoldPerSymbol() {
        PostService.Posted posted = posts.post("ann", "Hello World", null);

        assertThat(posted.goldSpent()).isEqualTo(10);
        assertThat(posted.post().paid()).isEqualTo(10);
        assertThat(posted.post().authorName()).isEqualTo("ann");
        verify(purse).spendGold("ann", 10);
        assertThat(posts.find(posted.post().id()).text()).isEqualTo("Hello World");
    }

    @Test
    void withoutTheGoldNothingIsPosted() {
        when(purse.spendGold(anyString(), anyInt())).thenThrow(new NotEnoughGoldException(3, 10));

        assertThatThrownBy(() -> posts.post("ann", "Hello World", null))
                .isInstanceOf(NotEnoughGoldException.class);
        assertThat(mongo.count(new Query(), Post.class)).isZero();
    }

    @Test
    void aBlankPostIsRejectedBeforeSpending() {
        assertThatThrownBy(() -> posts.post("ann", "  \n ", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(purse, never()).spendGold(anyString(), anyInt());
    }

    @Test
    void repliesAreOneLevelDeep() {
        Post post = posts.post("ann", "Floor 3 has a troll", null).post();
        Post reply = posts.post("bob", "Bring a bigger stick", post.id()).post();
        posts.post("cat", "Or run", post.id());

        assertThat(posts.find(post.id()).replyCount()).isEqualTo(2);
        assertThat(posts.replies(post.id())).extracting(Post::text)
                .containsExactly("Bring a bigger stick", "Or run");
        assertThatThrownBy(() -> posts.post("ann", "No", reply.id()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEditPaysOnlyForSymbolsPastWhatWasPaid() {
        String id = posts.post("ann", "abcdefghij", null).post().id();

        assertThat(posts.edit("ann", id, "abcdefghijklmno").goldSpent()).isEqualTo(5);
        assertThat(posts.edit("ann", id, "abc").goldSpent()).as("shorter").isZero();
        assertThat(posts.edit("ann", id, "zyxwvutsrqponml").goldSpent()).as("back to 15").isZero();
        assertThat(posts.edit("ann", id, "zyxwvutsrqponmlk").goldSpent()).isEqualTo(1);

        Post post = posts.find(id);
        assertThat(post.text()).isEqualTo("zyxwvutsrqponmlk");
        assertThat(post.paid()).isEqualTo(16);
        assertThat(post.editedAt()).isNotNull();
    }

    @Test
    void onlyTheAuthorCanEdit() {
        String id = posts.post("ann", "mine", null).post().id();

        assertThatThrownBy(() -> posts.edit("bob", id, "yours"))
                .isInstanceOf(NotYourPostException.class);
    }

    @Test
    void aPostNobodyRepliedToIsRemovedWithItsLikes() {
        String id = posts.post("ann", "oops", null).post().id();
        posts.like("bob", id);

        assertThat(posts.delete("ann", id)).isEmpty();
        assertThatThrownBy(() -> posts.find(id)).isInstanceOf(NoSuchElementException.class);
        assertThat(mongo.count(new Query(), Like.class)).isZero();
        verify(purse, never()).refundGold(anyString(), anyInt());
    }

    @Test
    void aPostWithRepliesStaysAsATombstone() {
        String id = posts.post("ann", "Floor 3 has a troll", null).post().id();
        posts.post("bob", "Bring a bigger stick", id);
        posts.like("bob", id);

        Post tombstone = posts.delete("ann", id).orElseThrow();

        assertThat(tombstone.text()).isNull();
        assertThat(tombstone.deletedAt()).isNotNull();
        assertThat(tombstone.likeCount()).isZero();
        assertThat(posts.replies(id)).extracting(Post::text).containsExactly("Bring a bigger stick");
        assertThat(posts.feed("ann", null)).extracting(Post::id).containsExactly(id);
        assertThat(posts.delete("ann", id)).as("again").contains(tombstone);
        assertThatThrownBy(() -> posts.post("cat", "What troll?", id)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> posts.edit("ann", id, "back")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> posts.like("cat", id)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletingAReplyLowersTheCountAndTheLastOneTakesTheTombstone() {
        String id = posts.post("ann", "Floor 3 has a troll", null).post().id();
        String bobs = posts.post("bob", "Bring a bigger stick", id).post().id();
        String cats = posts.post("cat", "Or run", id).post().id();

        assertThat(posts.delete("bob", bobs)).isEmpty();
        assertThat(posts.find(id).replyCount()).isEqualTo(1);

        posts.delete("ann", id);
        posts.delete("cat", cats);
        assertThatThrownBy(() -> posts.find(id)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void onlyTheAuthorCanDelete() {
        String id = posts.post("ann", "mine", null).post().id();

        assertThatThrownBy(() -> posts.delete("bob", id)).isInstanceOf(NotYourPostException.class);
        assertThat(posts.find(id).text()).isEqualTo("mine");
    }

    @Test
    void eachPlayerLikesAPostOnce() {
        String id = posts.post("ann", "like me", null).post().id();

        assertThat(posts.like("bob", id).likeCount()).isEqualTo(1);
        assertThat(posts.like("bob", id).likeCount()).as("again").isEqualTo(1);
        assertThat(posts.like("cat", id).likeCount()).isEqualTo(2);
        assertThat(posts.unlike("bob", id).likeCount()).isEqualTo(1);
        assertThat(posts.unlike("bob", id).likeCount()).as("again").isEqualTo(1);
    }

    @Test
    void aPageOfPostsKnowsWhichOnesYouLiked() {
        String a = posts.post("ann", "a", null).post().id();
        String b = posts.post("ann", "b", null).post().id();
        String c = posts.post("ann", "c", null).post().id();
        posts.like("bob", a);
        posts.like("bob", c);
        posts.like("cat", b);

        assertThat(posts.likedAmong("bob", List.of(a, b, c))).containsExactlyInAnyOrder(a, c);
        assertThat(posts.likedAmong("cat", List.of(a, c))).isEmpty();
        assertThat(posts.likedAmong("bob", List.of())).isEmpty();
    }

    @Test
    void theFeedShowsWhoYouFollowNewestFirst() {
        follows.follow("ann", "bob");
        follows.follow("ann", "bob");
        Post first = posts.post("bob", "one", null).post();
        posts.post("cat", "not followed", null);
        posts.post("ann", "two", null);
        posts.post("ann", "a reply", first.id());
        posts.post("bob", "three", null);

        assertThat(posts.feed("ann", null)).extracting(Post::text).containsExactly("three", "two", "one");
        assertThat(posts.feed("cat", null)).extracting(Post::text).containsExactly("not followed");

        follows.unfollow("ann", "bob");
        assertThat(posts.feed("ann", null)).extracting(Post::text).containsExactly("two");
    }

    @Test
    void theFeedPagesByTheLastPostSeen() {
        for (int i = 1; i <= PostService.PAGE + 5; i++) {
            posts.post("ann", "post " + i, null);
        }

        List<Post> page1 = posts.feed("ann", null);
        List<Post> page2 = posts.feed("ann", page1.getLast().id());

        assertThat(page1).hasSize(PostService.PAGE);
        assertThat(page1.getFirst().text()).isEqualTo("post 25");
        assertThat(page2).extracting(Post::text)
                .containsExactly("post 5", "post 4", "post 3", "post 2", "post 1");
    }

    @Test
    void youCantFollowYourself() {
        assertThatThrownBy(() -> follows.follow("ann", "ann"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
