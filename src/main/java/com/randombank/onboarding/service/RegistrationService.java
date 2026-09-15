package com.randombank.onboarding.service;

import com.randombank.onboarding.domain.entity.Customer;
import com.randombank.onboarding.dto.request.RegisterRequest;

/**
 * Registers a new customer and opens their account.
 */
public interface RegistrationService {

    /**
     * Creates a customer together with a current account, in a single transaction.
     *
     * @param request validated registration details
     * @return the persisted customer, including the generated default password
     * @throws com.randombank.onboarding.exception.ConflictException if the username is taken
     */
    Customer registerCustomer(RegisterRequest request);
}
