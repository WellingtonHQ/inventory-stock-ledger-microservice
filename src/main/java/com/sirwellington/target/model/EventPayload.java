package com.sirwellington.target.model;

import java.math.BigDecimal;

public record EventPayload(
    long transactionId,
    String type,
    String skuId,
    int quantityChange,
    BigDecimal unitCost
) {}
