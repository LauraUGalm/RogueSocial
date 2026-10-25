package com.roguesocial.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

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
 *
 * <p>Posts go live at once here, so these tests needn't wait out the grace period.
 * {@link GracePeriodTest} tests the minute.
 */
@SpringBootTest(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/rogue_social_test",
        "rogue.post-grace=PT0S" })
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
    private FriendService friends;

    @Autowired
    private BlockService blocks;

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
        mongo.remove(new Query(), Friendship.class);
        mongo.remove(new Query(), Block.class);
        for (String name : List.of("ann", "bob", "cat")) {
            when(users.findByUsername(name)).thenReturn(Optional.of(new User(
                    name.charAt(0), name, name + "@example.com", OffsetDateTime.now(), 1, 5, 5, 20)));
        }
        // Each test user's ID is the first letter of their name, so 'a' (97) is ann.
        when(users.usernames(any())).thenAnswer(call -> ((Collection<Long>) call.getArgument(0)).stream()
                .collect(Collectors.toMap(id -> id, id -> Map.of('a', "ann", 'b', "bob", 'c', "cat")
                        .get((char) id.longValue()))));
    }

    @Test
    void postingCostsOneGoldPerSymbol() {
        PostService.Posted posted = posts.post("ann", "Hello World", null, null);

        assertThat(posted.goldSpent()).isEqualTo(10);
        assertThat(posted.post().paid()).isEqualTo(10);
        assertThat(posted.post().authorName()).isEqualTo("ann");
        verify(purse).spendGold("ann", 10);
        assertThat(posts.find(posted.post().id()).text()).isEqualTo("Hello World");
    }

    @Test
    void withoutTheGoldNothingIsPosted() {
        when(purse.spendGold(anyString(), anyInt())).thenThrow(new NotEnoughGoldException(3, 10));

        assertThatThrownBy(() -> posts.post("ann", "Hello World", null, null))
                .isInstanceOf(NotEnoughGoldException.class);
        assertThat(mongo.count(new Query(), Post.class)).isZero();
    }

    @Test
    void aBlankPostIsRejectedBeforeSpending() {
        assertThatThrownBy(() -> posts.post("ann", "  \n ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(purse, never()).spendGold(anyString(), anyInt());
    }

    @Test
    void repliesAreOneLevelDeep() {
        follows.follow("bob", "ann");
        follows.follow("cat", "ann");
        Post post = posts.post("ann", "Floor 3 has a troll", null, null).post();
        Post reply = posts.post("bob", "Bring a bigger stick", post.id(), null).post();
        posts.post("cat", "Or run", post.id(), null);

        assertThat(posts.find(post.id()).replyCount()).isEqualTo(2);
        assertThat(posts.replies("ann", post.id())).extracting(Post::text)
                .containsExactly("Bring a bigger stick", "Or run");
        assertThatThrownBy(() -> posts.post("ann", "No", reply.id(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEditPaysOnlyForSymbolsPastWhatWasPaid() {
        String id = posts.post("ann", "abcdefghij", null, null).post().id();

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
        String id = posts.post("ann", "mine", null, null).post().id();

        assertThatThrownBy(() -> posts.edit("bob", id, "yours"))
                .isInstanceOf(NotYourPostException.class);
    }

    @Test
    void aPostNobodyRepliedToIsRemovedWithItsLikes() {
        follows.follow("bob", "ann");
        String id = posts.post("ann", "oops", null, null).post().id();
        posts.like("bob", id);

        assertThat(posts.delete("ann", id)).isEmpty();
        assertThatThrownBy(() -> posts.find(id)).isInstanceOf(NoSuchElementException.class);
        assertThat(mongo.count(new Query(), Like.class)).isZero();
        verify(purse, never()).refundGold(anyString(), anyInt());
    }

    @Test
    void aPostWithRepliesStaysAsATombstone() {
        follows.follow("bob", "ann");
        follows.follow("cat", "ann");
        String id = posts.post("ann", "Floor 3 has a troll", null, null).post().id();
        posts.post("bob", "Bring a bigger stick", id, null);
        posts.like("bob", id);

        Post tombstone = posts.delete("ann", id).orElseThrow();

        assertThat(tombstone.text()).isNull();
        assertThat(tombstone.deletedAt()).isNotNull();
        assertThat(tombstone.likeCount()).isZero();
        assertThat(posts.replies("ann", id)).extracting(Post::text).containsExactly("Bring a bigger stick");
        assertThat(posts.feed("ann", null)).extracting(Post::id).containsExactly(id);
        assertThat(posts.delete("ann", id)).as("again").contains(tombstone);
        assertThatThrownBy(() -> posts.post("cat", "What troll?", id, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> posts.edit("ann", id, "back")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> posts.like("cat", id)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletingAReplyLowersTheCountAndTheLastOneTakesTheTombstone() {
        follows.follow("bob", "ann");
        follows.follow("cat", "ann");
        String id = posts.post("ann", "Floor 3 has a troll", null, null).post().id();
        String bobs = posts.post("bob", "Bring a bigger stick", id, null).post().id();
        String cats = posts.post("cat", "Or run", id, null).post().id();

        assertThat(posts.delete("bob", bobs)).isEmpty();
        assertThat(posts.find(id).replyCount()).isEqualTo(1);

        posts.delete("ann", id);
        posts.delete("cat", cats);
        assertThatThrownBy(() -> posts.find(id)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void onlyTheAuthorCanDelete() {
        String id = posts.post("ann", "mine", null, null).post().id();

        assertThatThrownBy(() -> posts.delete("bob", id)).isInstanceOf(NotYourPostException.class);
        assertThat(posts.find(id).text()).isEqualTo("mine");
    }

    @Test
    void eachPlayerLikesAPostOnce() {
        follows.follow("bob", "ann");
        follows.follow("cat", "ann");
        String id = posts.post("ann", "like me", null, null).post().id();

        assertThat(posts.like("bob", id).likeCount()).isEqualTo(1);
        assertThat(posts.like("bob", id).likeCount()).as("again").isEqualTo(1);
        assertThat(posts.like("cat", id).likeCount()).isEqualTo(2);
        assertThat(posts.unlike("bob", id).likeCount()).isEqualTo(1);
        assertThat(posts.unlike("bob", id).likeCount()).as("again").isEqualTo(1);
    }

    @Test
    void aPageOfPostsKnowsWhichOnesYouLiked() {
        follows.follow("bob", "ann");
        follows.follow("cat", "ann");
        String a = posts.post("ann", "a", null, null).post().id();
        String b = posts.post("ann", "b", null, null).post().id();
        String c = posts.post("ann", "c", null, null).post().id();
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
        Post first = posts.post("bob", "one", null, null).post();
        posts.post("cat", "not followed", null, null);
        posts.post("ann", "two", null, null);
        posts.post("ann", "a reply", first.id(), null);
        posts.post("bob", "three", null, null);

        assertThat(posts.feed("ann", null)).extracting(Post::text).containsExactly("three", "two", "one");
        assertThat(posts.feed("cat", null)).extracting(Post::text).containsExactly("not followed");

        follows.unfollow("ann", "bob");
        assertThat(posts.feed("ann", null)).extracting(Post::text).containsExactly("two");
    }

    @Test
    void theFeedPagesByTheLastPostSeen() {
        for (int i = 1; i <= PostService.PAGE + 5; i++) {
            posts.post("ann", "post " + i, null, null);
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

    @Test
    void aFriendRequestWaitsUntilTheOtherAsksBack() {
        friends.request("ann", "bob");
        friends.request("ann", "bob");

        assertThat(friends.friends("ann")).isEqualTo(new FriendService.Friends(List.of(), List.of(), List.of("bob")));
        assertThat(friends.friends("bob")).isEqualTo(new FriendService.Friends(List.of(), List.of("ann"), List.of()));

        friends.request("bob", "ann");

        assertThat(friends.friends("ann").friends()).containsExactly("bob");
        assertThat(friends.friends("bob").friends()).containsExactly("ann");
        assertThat(mongo.count(new Query(), Friendship.class)).as("one document for the pair").isEqualTo(1);
    }

    @Test
    void endingAFriendshipEndsItForBoth() {
        friends.request("ann", "bob");
        friends.request("bob", "ann");
        friends.request("cat", "ann");

        friends.end("bob", "ann");
        friends.end("ann", "cat");

        assertThat(friends.friends("ann")).isEqualTo(new FriendService.Friends(List.of(), List.of(), List.of()));
        assertThat(friends.friends("bob").friends()).isEmpty();
        assertThat(friends.friends("cat").outgoing()).isEmpty();
    }

    @Test
    void theFeedShowsFriendsButNotPendingRequests() {
        posts.post("bob", "from a friend", null, null);
        friends.request("ann", "bob");

        assertThat(posts.feed("ann", null)).as("only asked").isEmpty();

        friends.request("bob", "ann");

        assertThat(posts.feed("ann", null)).extracting(Post::text).containsExactly("from a friend");
        assertThat(posts.feed("bob", null)).extracting(Post::text).containsExactly("from a friend");
    }

    @Test
    void youCantBeYourOwnFriend() {
        assertThatThrownBy(() -> friends.request("ann", "ann"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFriendsPostIsOnlyForFriends() {
        friends.request("ann", "bob");
        friends.request("bob", "ann");
        follows.follow("cat", "ann");
        Post secret = posts.post("ann", "for friends", null, Post.Audience.FRIENDS).post();
        posts.post("ann", "for everyone", null, null);

        assertThat(posts.feed("bob", null)).extracting(Post::text).containsExactly("for everyone", "for friends");
        assertThat(posts.feed("cat", null)).extracting(Post::text).containsExactly("for everyone");
        assertThat(posts.feed("ann", null)).hasSize(2);

        assertThat(posts.find("bob", secret.id()).text()).isEqualTo("for friends");
        assertThatThrownBy(() -> posts.find("cat", secret.id())).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> posts.replies("cat", secret.id())).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> posts.like("cat", secret.id())).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> posts.post("cat", "let me in", secret.id(), null))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void aReplyGoesToItsPostsAudience() {
        friends.request("ann", "bob");
        friends.request("bob", "ann");
        Post secret = posts.post("ann", "for friends", null, Post.Audience.FRIENDS).post();

        Post reply = posts.post("bob", "me too", secret.id(), Post.Audience.FOLLOWERS).post();

        assertThat(reply.audience()).isEqualTo(Post.Audience.FRIENDS);
        assertThatThrownBy(() -> posts.like("cat", reply.id())).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void unfriendingHidesTheirFriendsPosts() {
        friends.request("ann", "bob");
        friends.request("bob", "ann");
        follows.follow("bob", "ann");
        Post secret = posts.post("ann", "for friends", null, Post.Audience.FRIENDS).post();

        friends.end("ann", "bob");

        assertThat(posts.feed("bob", null)).isEmpty();
        assertThatThrownBy(() -> posts.find("bob", secret.id())).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void postsFromBeforeAudiencesAreForFollowers() {
        follows.follow("bob", "ann");
        mongo.insert(new org.bson.Document("authorId", (long) 'a').append("authorName", "ann")
                .append("text", "old").append("paid", 3).append("likeCount", 0).append("replyCount", 0), "posts");

        assertThat(posts.feed("bob", null)).extracting(Post::text).containsExactly("old");
    }

    @Test
    void blockingEndsFriendshipAndFollowsBothWays() {
        friends.request("ann", "bob");
        friends.request("bob", "ann");
        follows.follow("ann", "bob");
        follows.follow("bob", "ann");
        posts.post("ann", "from ann", null, null);
        posts.post("bob", "from bob", null, null);

        blocks.block("ann", "bob");
        blocks.block("ann", "bob");

        assertThat(friends.friends("ann").friends()).isEmpty();
        assertThat(friends.friends("bob").friends()).isEmpty();
        assertThat(mongo.count(new Query(), Follow.class)).isZero();
        assertThat(posts.feed("ann", null)).extracting(Post::text).containsExactly("from ann");
        assertThat(posts.feed("bob", null)).extracting(Post::text).containsExactly("from bob");
        assertThat(blocks.blocked("ann")).containsExactly("bob");
        assertThat(blocks.blocked("bob")).isEmpty();
    }

    @Test
    void aBlockStopsNewFollowsAndRequestsEitherWay() {
        blocks.block("ann", "bob");

        assertThatThrownBy(() -> follows.follow("ann", "bob"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unblock");
        assertThatThrownBy(() -> friends.request("ann", "bob")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> follows.follow("bob", "ann"))
                .isInstanceOf(NoSuchElementException.class).hasMessage("No user ann");
        assertThatThrownBy(() -> friends.request("bob", "ann")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void aBlockHidesPostsAndRepliesBothWays() {
        follows.follow("bob", "ann");
        follows.follow("bob", "cat");
        follows.follow("ann", "cat");
        Post anns = posts.post("ann", "by ann", null, null).post();
        Post cats = posts.post("cat", "by cat", null, null).post();
        Post bobsReply = posts.post("bob", "bob replies", cats.id(), null).post();
        posts.post("ann", "ann replies", cats.id(), null);

        blocks.block("ann", "bob");

        assertThatThrownBy(() -> posts.find("bob", anns.id())).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> posts.like("bob", anns.id())).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> posts.post("bob", "hey", anns.id(), null)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> posts.like("ann", bobsReply.id())).isInstanceOf(NoSuchElementException.class);
        assertThat(posts.replies("ann", cats.id())).extracting(Post::text).containsExactly("ann replies");
        assertThat(posts.replies("bob", cats.id())).extracting(Post::text).containsExactly("bob replies");
        assertThat(posts.replies("cat", cats.id())).hasSize(2);
    }

    @Test
    void unblockingStartsOver() {
        friends.request("ann", "bob");
        friends.request("bob", "ann");
        blocks.block("ann", "bob");

        blocks.unblock("ann", "bob");

        assertThat(friends.friends("ann").friends()).isEmpty();
        assertThat(blocks.blocked("ann")).isEmpty();
        follows.follow("bob", "ann");
        assertThat(posts.feed("bob", null)).isEmpty();
    }

    @Test
    void onlyTheBlockerCanUnblock() {
        blocks.block("ann", "bob");

        blocks.unblock("bob", "ann");

        assertThat(blocks.blocked("ann")).containsExactly("bob");
    }

    @Test
    void aFollowersPostNeedsAFollowToOpen() {
        Post post = posts.post("ann", "for followers", null, null).post();

        assertThatThrownBy(() -> posts.find("cat", post.id())).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> posts.like("cat", post.id())).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> posts.post("cat", "hi", post.id(), null)).isInstanceOf(NoSuchElementException.class);

        follows.follow("cat", "ann");
        assertThat(posts.find("cat", post.id()).text()).isEqualTo("for followers");

        follows.unfollow("cat", "ann");
        friends.request("ann", "cat");
        friends.request("cat", "ann");
        assertThat(posts.find("cat", post.id()).text()).as("a friend who doesn't follow").isEqualTo("for followers");
    }
}
