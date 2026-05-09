package com.msc.church.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * Locale resolution: {@code Accept-Language} header → fall back to Korean.
 * Per-user preference (members.preferred_locale) is layered on top in a higher-level
 * filter once the auth filter is running — V1 keeps this resolver simple.
 *
 * <p>The {@link org.springframework.context.MessageSource} itself is auto-configured by
 * Spring Boot from {@code spring.messages.*} in {@code application.yml}; defining a
 * custom one here would shadow that, so we don't.
 */
@Configuration
public class MessageConfig {

    public static final Locale DEFAULT_LOCALE = Locale.KOREAN;

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(DEFAULT_LOCALE);
        resolver.setSupportedLocales(List.of(Locale.KOREAN, Locale.ENGLISH));
        return resolver;
    }
}
