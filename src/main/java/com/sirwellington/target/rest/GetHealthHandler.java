package com.sirwellington.target.rest;

import com.google.inject.Singleton;
import io.javalin.http.Context;
import java.util.Map;

@Singleton
public class GetHealthHandler {

    public void handle(Context ctx) {
        ctx.json(Map.of("status", "ok"));
    }
}
