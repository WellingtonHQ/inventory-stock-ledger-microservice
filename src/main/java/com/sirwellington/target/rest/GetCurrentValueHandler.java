package com.sirwellington.target.rest;

import java.util.Map;
import java.util.Objects;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sirwellington.target.db.InventoryRepository;
import io.javalin.http.Context;
import tech.sirwellington.alchemy.annotations.arguments.Required;

@Singleton
public class GetCurrentValueHandler {
    private final InventoryRepository repository;

    @Inject
    public GetCurrentValueHandler(@Required InventoryRepository repository) {
        Objects.requireNonNull(repository);
        this.repository = repository;
    }

    public void handle(Context ctx) throws Exception {
        var skuId = ctx.pathParam("skuId");

        var result = repository.getInventoryValue(
            new InventoryRepository.GetInventoryValueRequest(skuId)
        );

        if (result.isEmpty()) {
            ctx.status(404).json(Map.of("error", "SKU not found: " + skuId));
            return;
        }

        var value = result.get();
        ctx.json(new CurrentValueResponse(skuId, value.currentQuantity(), value.totalCurrentValue()));
    }

    public record CurrentValueResponse(
        String skuId,
        int currentQuantity,
        java.math.BigDecimal totalCurrentValue
    ) {}
}
