package com.scimplayground.validator.mgmt.repo;

import com.scimplayground.validator.mgmt.model.ValidationHttpExchange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ValidationHttpExchangeRepository extends JpaRepository<ValidationHttpExchange, UUID> {

    List<ValidationHttpExchange> findByTestResultIdOrderBySequenceNumberAsc(UUID testResultId);
}
