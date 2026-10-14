package com.ccm.common.repository;

import com.ccm.common.entity.ClientDetails;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientDetailsRepository extends JpaRepository<ClientDetails, String> {

    boolean existsByClientId(String clientId);
}
