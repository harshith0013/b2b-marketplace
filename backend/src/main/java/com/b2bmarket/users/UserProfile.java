package com.b2bmarket.users;

import java.util.UUID;

public record UserProfile(UUID id, String email, String displayName, String preferredLocale) {}
