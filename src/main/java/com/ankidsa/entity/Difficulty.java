package com.ankidsa.entity;

/**
 * WHY AN ENUM AND NOT A STRING?
 *
 * You could store difficulty as a plain String ("EASY", "MEDIUM", "HARD").
 * But then someone could accidentally send "easy", "Easy", "EAZY", or
 * "NIGHTMARE" — and your database would happily accept all of them.
 *
 * An enum restricts the value to EXACTLY these 3 options.
 * Combined with @Enumerated(EnumType.STRING) on the entity field,
 * Hibernate will store "EASY"/"MEDIUM"/"HARD" as text in PostgreSQL
 * and reject anything else at the Java level before it hits the DB.
 *
 * WHY EnumType.STRING not EnumType.ORDINAL?
 * ORDINAL stores 0, 1, 2 (the index). If you later add a new value
 * between EASY and MEDIUM, all existing data shifts meaning.
 * STRING stores the actual name — safer, self-documenting in the DB.
 */
public enum Difficulty {
    EASY,
    MEDIUM,
    HARD
}
