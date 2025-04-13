package dev.elvis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Factory
public class ObjectMapperFactory {
    private static final Logger log = LoggerFactory.getLogger(ObjectMapperFactory.class);

    @Singleton
    @Named("externalServiceObjectMapper") // Named ObjectMapper bean for CAMEL_CASE
    public ObjectMapper externalServiceObjectMapper() {
        log.info("Creating '@Named(\"externalServiceObjectMapper\")' (CAMEL_CASE) bean");
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}