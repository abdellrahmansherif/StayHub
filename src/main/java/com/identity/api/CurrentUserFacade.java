package com.identity.api;

import java.util.UUID;

public interface CurrentUserFacade {
    // Returns the UUID of the currently authenticated user.
    UUID getCurrentUserId();
    // Checks whether the current user has the admin role.
    boolean isAdmin();
    // Checks whether the current user has the customer role
    boolean isCustomer();


}
