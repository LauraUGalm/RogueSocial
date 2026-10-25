package com.roguesocial.social;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * What a player wrote about themselves: one document in {@code profiles}, made the first time
 * they save anything.
 *
 * <p>Its {@code _id} is the player's user ID rather than an ObjectId. {@code _id} can be any
 * value, and when each player has exactly one profile, their ID is the natural key: looking a
 * profile up is a look-up on {@code _id}, which Mongo always indexes, so no other index is
 * needed.
 *
 * <p>Pictures aren't in here. They are files in GridFS, found by their owner (see
 * {@link ProfileService}).
 *
 * @param location where they are, in their own words, like "Ohio"
 * @param status   a short line that changes, like "Hunting trolls on floor 3"
 * @param statusAt when the status last changed, so it can say "2h ago"
 */
@Document("profiles")
record Profile(@Id long userId, String about, String location, String status, Instant statusAt) {
}
