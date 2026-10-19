package com.identity.api;

import java.util.UUID;

public interface CurrentUserFacade {
    UUID getCurrentUserId();

    boolean isAdmin();

    boolean isCustomer();


}
