package com.sirwellington.target.rest;

import java.util.Map;

import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.sirwellington.alchemy.test.AlchemyTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@AlchemyTest
class GetHealthHandlerTest {

    @Test
    void testReturnsOkStatus() throws Exception {
        var ctx = mock(Context.class);
        var handler = new GetHealthHandler();
        handler.handle(ctx);

        var captor = ArgumentCaptor.forClass(Map.class);
        verify(ctx).json(captor.capture());
        var jsonMap = captor.getValue();
        var status = jsonMap.get("status");
        assertThat(status).isEqualTo("ok");
    }
}
