package com.lanmessenger;

import java.time.LocalDate;

public final class UserProfile {
    public final String localId;
    public final String name;
    public final String email;
    public final String username;
    public final LocalDate birthdate;
    public final String profilePic;
    public final String bio;

    public UserProfile(String localId, String name, String email, String username, LocalDate birthdate, String profilePic, String bio) {
        this.localId = localId;
        this.name = name;
        this.email = email;
        this.username = username;
        this.birthdate = birthdate;
        this.profilePic = profilePic;
        this.bio = bio;
    }
}
