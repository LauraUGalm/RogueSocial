package com.roguesocial.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Instant;
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
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.roguesocial.user.User;
import com.roguesocial.user.UserBank;
import com.roguesocial.user.UserRepository;

/**
 * The minute after posting, when only the author sees a post and can take it back. Set up like
 * {@link PostServiceTest}, but with the real minute. Rather than wait it out, a test "lets the
 * minute pass" by moving the post's {@code liveAt} into the past.
 */
@SpringBootTest(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/rogue_social_test",
        "rogue.post-grace=PT1M" })
@EnabledIf("com.roguesocial.social.PostServiceTest#mongoIsRunning")
class GracePeriodTest {

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
        mongo.remove(new Query(), Post.class);
        mongo.remove(new Query(), Follow.class);
        mongo.remove(new Query(), Like.class);
        mongo.remove(new Query(), Friendship.class);
        mongo.remove(new Query(), Block.class);
        for (String name : List.of("ann", "bob")) {
            when(users.findByUsername(name)).thenReturn(Optional.of(new User(
                    name.charAt(0), name, name + "@example.com", OffsetDateTime.now(), 1, 5, 5, 20)));
        }
        follows.follow("bob", "ann");
    }

    private void minutePasses(String postId) {
        mongo.updateFirst(query(where("_id").is(postId)),
                new Update().set("liveAt", Instant.now().minusSeconds(1)), Post.class);
    }

    @Test
    void forAMinuteOnlyTheAuthorSeesIt() {
        Post post = posts.post("ann", "hello", null, null).post();

        assertThat(post.liveAt()).isEqualTo(post.createdAt().plusSeconds(60));
        assertThat(posts.feed("ann", null)).extracting(Post::text).containsExactly("hello");
        assertThat(posts.feed("bob", null)).isEmpty();
        assertThatThrownBy(() -> posts.find("bob", post.id())).isInstanceOf(NoSuchElementException.class);

        minutePasses(post.id());

        assertThat(posts.feed("bob", null)).extracting(Post::text).containsExactly("hello");
    }

    @Test
    void takingItBackRefundsEverythingItCostEditsIncluded() {
        String id = posts.post("ann", "abcde", null, null).post().id();
        posts.edit("ann", id, "abcdefgh");

        assertThat(posts.takeBack("ann", id)).isEqualTo(8);

        verify(purse).refundGold("ann", 8);
        assertThatThrownBy(() -> posts.find(id)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void onceLiveItCantBeTakenBack() {
        String id = posts.post("ann", "too late", null, null).post().id();
        minutePasses(id);

        assertThatThrownBy(() -> posts.takeBack("ann", id)).isInstanceOf(IllegalStateException.class);
        assertThat(posts.find(id).text()).isEqualTo("too late");
        verify(purse, never()).refundGold(anyString(), anyInt());
    }

    @Test
    void onlyTheAuthorCanTakeItBack() {
        String id = posts.post("ann", "mine", null, null).post().id();

        assertThatThrownBy(() -> posts.takeBack("bob", id)).isInstanceOf(NotYourPostException.class);
    }

    @Test
    void itCantBeLikedOrRepliedToUntilLive() {
        String id = posts.post("ann", "wait for it", null, null).post().id();

        assertThatThrownBy(() -> posts.like("ann", id)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> posts.post("ann", "me first", id, null)).isInstanceOf(IllegalArgumentException.class);

        minutePasses(id);

        assertThat(posts.like("bob", id).likeCount()).isEqualTo(1);
    }

    @Test
    void aNewReplyIsHiddenFromOthersAndTakingItBackUncountsIt() {
        String id = posts.post("ann", "a post", null, null).post().id();
        minutePasses(id);
        String reply = posts.post("bob", "a reply", id, null).post().id();

        assertThat(posts.replies("ann", id)).isEmpty();
        assertThat(posts.replies("bob", id)).extracting(Post::text).containsExactly("a reply");
        assertThat(posts.find(id).replyCount()).isEqualTo(1);

        posts.takeBack("bob", reply);

        assertThat(posts.find(id).replyCount()).isZero();
    }
}
