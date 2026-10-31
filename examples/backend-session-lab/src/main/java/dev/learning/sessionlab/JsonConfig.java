package dev.learning.sessionlab;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class JsonConfig {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictInput() {
        return builder -> builder.featuresToEnable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .postConfigurer(mapper -> {
                var textual = mapper.coercionConfigFor(LogicalType.Textual);
                textual.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                textual.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                textual.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
            });
    }
}
