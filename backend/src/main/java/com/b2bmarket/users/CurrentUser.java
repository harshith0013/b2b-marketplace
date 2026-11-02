package com.b2bmarket.users;

import java.util.UUID;

/**
 * The signed-in person making the current request. Controllers receive it as a method parameter; it
 * is resolved from the verified access token.
 */
public record CurrentUser(UUID userId, String email) {}
